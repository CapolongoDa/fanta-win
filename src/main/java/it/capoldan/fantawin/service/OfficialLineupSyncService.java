package it.capoldan.fantawin.service;

import it.capoldan.fantawin.config.FantaWinConfigs;
import it.capoldan.fantawin.dto.AvailabilityReportDto;
import it.capoldan.fantawin.dto.PlayerDto;
import it.capoldan.fantawin.dto.RosterDto;
import it.capoldan.fantawin.dto.RosterPlayerDto;
import it.capoldan.fantawin.exception.ExceptionsCodes;
import it.capoldan.fantawin.exception.NotFoundException;
import it.capoldan.fantawin.generated.openapi.msclient.sportmonks.model.FixturesResponse;
import it.capoldan.fantawin.generated.openapi.msclient.sportmonks.model.SmFixture;
import it.capoldan.fantawin.generated.openapi.msclient.sportmonks.model.SmLineupPlayer;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.OfficialLineupSyncResult;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.PlayerAvailabilityUpdate;
import it.capoldan.fantawin.middleware.dao.dynamo.AvailabilityReportDao;
import it.capoldan.fantawin.middleware.dao.dynamo.PlayerDao;
import it.capoldan.fantawin.middleware.dao.dynamo.RosterDao;
import it.capoldan.fantawin.middleware.externalclient.sportmonks.SportMonksClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Legge da SportMonks la formazione ufficiale (endpoint fixtures?include=lineups, non appena
 * comunicata, di norma ~1h prima del calcio d'inizio) e aggiorna in automatico
 * AvailabilityReport.startingProbability dei giocatori in rosa: 100 se confermati titolari, 0 se
 * confermati in panchina. I giocatori non trovati nella formazione (non ancora pubblicata, squadra
 * non risolta su SportMonks o nome non corrispondente) non vengono toccati.
 */
@Slf4j
@Service
public class OfficialLineupSyncService {

    // Finestra di date interrogata su SportMonks attorno ad "oggi": non abbiamo una mappatura
    // affidabile giornata->data (FixtureDto non la conserva), quindi cerchiamo semplicemente la
    // partita piu' vicina nel tempo per ciascuna squadra. Pensato per essere chiamato a ridosso
    // del calcio d'inizio, non per giornate passate/future lontane.
    private static final int DAYS_BEFORE = 1;
    private static final int DAYS_AFTER = 3;
    private static final Integer STARTER_TYPE_ID = 11;

    private final RosterDao rosterDao;
    private final PlayerDao playerDao;
    private final AvailabilityReportDao availabilityReportDao;
    private final SportMonksClient sportMonksClient;
    private final SportMonksTeamIdResolver teamIdResolver;
    private final FantaWinConfigs fantaWinConfigs;

    public OfficialLineupSyncService(RosterDao rosterDao, PlayerDao playerDao,
                                     AvailabilityReportDao availabilityReportDao,
                                     SportMonksClient sportMonksClient,
                                     SportMonksTeamIdResolver teamIdResolver,
                                     FantaWinConfigs fantaWinConfigs) {
        this.rosterDao = rosterDao;
        this.playerDao = playerDao;
        this.availabilityReportDao = availabilityReportDao;
        this.sportMonksClient = sportMonksClient;
        this.teamIdResolver = teamIdResolver;
        this.fantaWinConfigs = fantaWinConfigs;
    }

    public Mono<OfficialLineupSyncResult> syncOfficialLineup(String rosterId) {
        log.info("Avvio sync formazione ufficiale SportMonks per rosterId={}", rosterId);
        return rosterDao.getById(rosterId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Rosa {} non trovata durante syncOfficialLineup", rosterId);
                    return Mono.error(new NotFoundException(
                            "Rosa " + rosterId + " non trovata", ExceptionsCodes.ERROR_CODE_NOT_FOUND));
                }))
                .flatMap(this::loadRosterPlayers)
                .flatMap(this::syncByTeam)
                .doOnSuccess(result -> log.info(
                        "Sync formazione ufficiale completata per rosterId={}: {} giocatori aggiornati, {} squadre non risolte",
                        rosterId, result.getUpdatedPlayers().size(), result.getUnresolvedTeams().size()))
                .doOnError(ex -> log.warn("Errore nella sync formazione ufficiale per rosterId={}", rosterId, ex));
    }

    private Mono<List<PlayerDto>> loadRosterPlayers(RosterDto roster) {
        List<RosterPlayerDto> rosterPlayers = roster.getPlayers() == null ? List.of() : roster.getPlayers();
        return Flux.fromIterable(rosterPlayers)
                .flatMap(rp -> playerDao.getById(rp.getPlayerId()))
                .collectList();
    }

    private Mono<OfficialLineupSyncResult> syncByTeam(List<PlayerDto> players) {
        Map<String, List<PlayerDto>> byTeam = players.stream()
                .filter(p -> p.getRealTeam() != null)
                .collect(Collectors.groupingBy(PlayerDto::getRealTeam));

        if (byTeam.isEmpty()) {
            log.warn("Nessun giocatore con realTeam valorizzato in rosa: nessuna sincronizzazione possibile");
            return Mono.just(buildResult(List.of(), List.of()));
        }

        return Flux.fromIterable(byTeam.entrySet())
                .flatMap(entry -> syncTeam(entry.getKey(), entry.getValue()))
                .collectList()
                .map(outcomes -> {
                    List<PlayerAvailabilityUpdate> updated = new ArrayList<>();
                    List<String> unresolved = new ArrayList<>();
                    outcomes.forEach(o -> {
                        updated.addAll(o.updates());
                        if (o.unresolvedTeam() != null) unresolved.add(o.unresolvedTeam());
                    });
                    return buildResult(updated, unresolved);
                });
    }

    private Mono<TeamSyncOutcome> syncTeam(String realTeam, List<PlayerDto> teamPlayers) {
        return teamIdResolver.resolveTeamId(realTeam)
                .flatMap(teamIdOpt -> {
                    if (teamIdOpt.isEmpty()) {
                        return Mono.just(TeamSyncOutcome.unresolved(realTeam));
                    }
                    return fetchLineup(realTeam, teamIdOpt.get())
                            .flatMap(lineupOpt -> lineupOpt
                                    .map(lineup -> matchAndUpdatePlayers(teamPlayers, realTeam, lineup)
                                            .map(TeamSyncOutcome::matched))
                                    .orElseGet(() -> Mono.just(TeamSyncOutcome.unresolved(realTeam))));
                });
    }

    private Mono<Optional<List<SmLineupPlayer>>> fetchLineup(String realTeam, Integer teamId) {
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        LocalDate today = LocalDate.now();
        String startDate = today.minusDays(DAYS_BEFORE).format(fmt);
        String endDate = today.plusDays(DAYS_AFTER).format(fmt);

        return sportMonksClient.getFixturesBetweenForTeam(teamId, startDate, endDate)
                .map(response -> selectFixture(realTeam, response).map(SmFixture::getLineups));
    }

    private Optional<SmFixture> selectFixture(String realTeam, FixturesResponse response) {
        List<SmFixture> fixtures = response == null || response.getData() == null ? List.of() : response.getData();
        Integer leagueId = fantaWinConfigs.getSportmonksSerieALeagueId();

        List<SmFixture> candidates = leagueId != null
                ? fixtures.stream().filter(f -> leagueId.equals(f.getLeagueId())).toList()
                : fixtures;

        if (candidates.isEmpty()) {
            log.info("Nessun fixture SportMonks trovato per realTeam={} nella finestra di date interrogata: " +
                    "nessuna partita in programma o formazione non ancora disponibile", realTeam);
            return Optional.empty();
        }
        if (candidates.size() > 1) {
            log.warn("Trovati {} fixture SportMonks per realTeam={} nella finestra di date interrogata " +
                            "(atteso 1): salto la squadra invece di indovinare quale sia quello giusto. " +
                            "Configura fantawin.sportmonks-serie-a-league-id per filtrare per lega.",
                    candidates.size(), realTeam);
            return Optional.empty();
        }
        SmFixture fixture = candidates.get(0);
        if (fixture.getLineups() == null || fixture.getLineups().isEmpty()) {
            log.info("Fixture SportMonks trovato per realTeam={} (id={}) ma formazione non ancora pubblicata",
                    realTeam, fixture.getId());
            return Optional.empty();
        }
        return Optional.of(fixture);
    }

    private Mono<List<PlayerAvailabilityUpdate>> matchAndUpdatePlayers(List<PlayerDto> teamPlayers, String realTeam,
                                                                        List<SmLineupPlayer> lineup) {
        return Flux.fromIterable(teamPlayers)
                .flatMap(player -> {
                    Optional<SmLineupPlayer> match = findMatch(player.getName(), lineup);
                    if (match.isEmpty()) {
                        log.info("Giocatore '{}' ({}) non trovato nella formazione ufficiale SportMonks di {}: " +
                                "AvailabilityReport non modificato", player.getName(), player.getPlayerId(), realTeam);
                        return Mono.<PlayerAvailabilityUpdate>empty();
                    }
                    SmLineupPlayer lineupPlayer = match.get();
                    boolean starter = STARTER_TYPE_ID.equals(lineupPlayer.getTypeId());
                    double startingProbability = starter ? 100.0 : 0.0;

                    AvailabilityReportDto toUpdate = AvailabilityReportDto.builder()
                            .playerId(player.getPlayerId())
                            .startingProbability(startingProbability)
                            .lastUpdated(Instant.now())
                            .build();

                    return availabilityReportDao.update(toUpdate)
                            .doOnSuccess(saved -> log.info(
                                    "Formazione ufficiale confermata per '{}' ({}): {} (startingProbability={})",
                                    player.getName(), player.getPlayerId(),
                                    starter ? "TITOLARE" : "PANCHINA", startingProbability))
                            .map(saved -> toApiUpdate(player, realTeam, starter, startingProbability))
                            // Un aggiornamento fallito per un singolo giocatore (es. rimosso nel frattempo)
                            // non deve far fallire l'intera sync delle altre squadre/giocatori: l'errore e'
                            // gia' loggato da AvailabilityReportDao.update, qui lo escludiamo dal risultato.
                            .onErrorResume(ex -> Mono.empty());
                })
                .collectList();
    }

    private static PlayerAvailabilityUpdate toApiUpdate(PlayerDto player, String realTeam, boolean starter, double startingProbability) {
        PlayerAvailabilityUpdate dto = new PlayerAvailabilityUpdate();
        dto.setPlayerId(player.getPlayerId());
        dto.setName(player.getName());
        dto.setRealTeam(realTeam);
        dto.setConfirmedStarter(starter);
        dto.setStartingProbability(startingProbability);
        return dto;
    }

    private static Optional<SmLineupPlayer> findMatch(String playerName, List<SmLineupPlayer> lineup) {
        if (playerName == null || lineup == null) return Optional.empty();
        String normalizedTarget = normalize(playerName);

        Optional<SmLineupPlayer> exact = lineup.stream()
                .filter(lp -> normalize(lp.getPlayerName()).equals(normalizedTarget))
                .findFirst();
        if (exact.isPresent()) return exact;

        // Fallback "al cognome": l'anagrafica potrebbe avere "Barella" e SportMonks "Nicolo Barella"
        // (o viceversa) - un contenimento reciproco su stringhe di almeno 3 caratteri evita falsi
        // positivi su nomi troppo corti mantenendo comunque il match utile nel caso comune.
        return lineup.stream()
                .filter(lp -> {
                    String candidate = normalize(lp.getPlayerName());
                    if (candidate.length() < 3 || normalizedTarget.length() < 3) return false;
                    return candidate.contains(normalizedTarget) || normalizedTarget.contains(candidate);
                })
                .findFirst();
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String noAccents = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccents.toLowerCase().replaceAll("\\s+", " ");
    }

    private static OfficialLineupSyncResult buildResult(List<PlayerAvailabilityUpdate> updated, List<String> unresolved) {
        OfficialLineupSyncResult result = new OfficialLineupSyncResult();
        result.setUpdatedPlayers(updated);
        result.setUnresolvedTeams(unresolved);
        return result;
    }

    private record TeamSyncOutcome(List<PlayerAvailabilityUpdate> updates, String unresolvedTeam) {
        static TeamSyncOutcome matched(List<PlayerAvailabilityUpdate> updates) {
            return new TeamSyncOutcome(updates, null);
        }
        static TeamSyncOutcome unresolved(String realTeam) {
            return new TeamSyncOutcome(List.of(), realTeam);
        }
    }
}
