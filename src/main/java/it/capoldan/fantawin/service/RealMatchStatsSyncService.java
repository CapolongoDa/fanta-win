package it.capoldan.fantawin.service;

import it.capoldan.fantawin.config.FantaWinConfigs;
import it.capoldan.fantawin.dto.PlayerDto;
import it.capoldan.fantawin.dto.PlayerMatchStatDto;
import it.capoldan.fantawin.dto.Role;
import it.capoldan.fantawin.dto.RosterDto;
import it.capoldan.fantawin.dto.RosterPlayerDto;
import it.capoldan.fantawin.exception.ExceptionsCodes;
import it.capoldan.fantawin.exception.InternalException;
import it.capoldan.fantawin.exception.NotFoundException;
import it.capoldan.fantawin.generated.openapi.msclient.sportmonks.model.RoundsResponse;
import it.capoldan.fantawin.generated.openapi.msclient.sportmonks.model.SmEvent;
import it.capoldan.fantawin.generated.openapi.msclient.sportmonks.model.SmFixture;
import it.capoldan.fantawin.generated.openapi.msclient.sportmonks.model.SmParticipant;
import it.capoldan.fantawin.generated.openapi.msclient.sportmonks.model.SmRound;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.MatchStatsSyncResult;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.PlayerMatchStatUpdate;
import it.capoldan.fantawin.middleware.dao.dynamo.PlayerDao;
import it.capoldan.fantawin.middleware.dao.dynamo.PlayerMatchStatDao;
import it.capoldan.fantawin.middleware.dao.dynamo.RosterDao;
import it.capoldan.fantawin.middleware.externalclient.sportmonks.SportMonksClient;
import it.capoldan.fantawin.utils.FantaVotoCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Sincronizza da SportMonks (endpoint /rounds/seasons/{seasonId}, include=fixtures.events;fixtures.participants)
 * gli eventi oggettivi reali di una giornata gia' disputata - gol, assist (related_player_id sull'evento Goal),
 * ammonizioni, espulsioni e, solo per portieri/difensori, gol subiti dalla squadra - e li scrive su
 * PlayerMatchStat per ogni giocatore in rosa.
 * <p>
 * Il voto (pagella) NON viene mai toccato da questo servizio: SportMonks fornisce solo eventi oggettivi,
 * non un giudizio editoriale sulla prestazione (quello resta manuale, tipicamente via
 * {@link PlayerMatchStatImportService}). Se per un giocatore non esiste ancora un voto per questa giornata,
 * il fantavoto ricalcolato resta null (coerente con {@link FantaVotoCalculator}: "senza voto, niente fantavoto"),
 * finche' qualcuno non valorizza il voto - a quel punto andra' ri-triggerata la sync (o corretto a mano) per
 * ricalcolare il fantavoto definitivo.
 * <p>
 * Una riga PlayerMatchStat gia' esistente per playerId+giornata viene arricchita preservando voto/opponentTeam/
 * season/home/xg/xa gia' presenti; se non esiste ancora viene creata con solo i campi automatizzati.
 */
@Slf4j
@Service
public class RealMatchStatsSyncService {

    // Type_id SportMonks (v. docs.sportmonks.com/v3/definitions/types/events): niente evento "Assist" a se'
    // stante, sull'evento Goal/Penalty il related_player_id porta l'assistman.
    private static final Integer GOAL_TYPE_ID = 14;
    private static final Integer OWN_GOAL_TYPE_ID = 15;
    private static final Integer PENALTY_TYPE_ID = 16;
    private static final Integer YELLOWCARD_TYPE_ID = 19;
    private static final Integer REDCARD_TYPE_ID = 20;
    private static final Integer YELLOWREDCARD_TYPE_ID = 21;

    private final RosterDao rosterDao;
    private final PlayerDao playerDao;
    private final PlayerMatchStatDao playerMatchStatDao;
    private final SportMonksClient sportMonksClient;
    private final FantaWinConfigs fantaWinConfigs;
    private final MatchStatsCsvExporter matchStatsCsvExporter;

    public RealMatchStatsSyncService(RosterDao rosterDao, PlayerDao playerDao,
                                     PlayerMatchStatDao playerMatchStatDao, SportMonksClient sportMonksClient,
                                     FantaWinConfigs fantaWinConfigs, MatchStatsCsvExporter matchStatsCsvExporter) {
        this.rosterDao = rosterDao;
        this.playerDao = playerDao;
        this.playerMatchStatDao = playerMatchStatDao;
        this.sportMonksClient = sportMonksClient;
        this.fantaWinConfigs = fantaWinConfigs;
        this.matchStatsCsvExporter = matchStatsCsvExporter;
    }

    public Mono<MatchStatsSyncResult> syncMatchStats(String rosterId, Integer matchday) {
        Integer seasonId = fantaWinConfigs.getSportmonksSerieASeasonId();
        if (seasonId == null) {
            log.warn("fantawin.sportmonks-serie-a-season-id non configurato: sync statistiche reali rifiutata");
            return Mono.error(new InternalException(
                    "fantawin.sportmonks-serie-a-season-id non configurato: impossibile sincronizzare le statistiche reali",
                    ExceptionsCodes.ERROR_CODE_GENERIC_ERROR));
        }
        log.info("Avvio sync statistiche reali SportMonks per rosterId={} matchday={}", rosterId, matchday);
        return rosterDao.getById(rosterId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Rosa {} non trovata durante syncMatchStats", rosterId);
                    return Mono.error(new NotFoundException(
                            "Rosa " + rosterId + " non trovata", ExceptionsCodes.ERROR_CODE_NOT_FOUND));
                }))
                .flatMap(this::loadRosterPlayers)
                .zipWith(sportMonksClient.getRoundsForSeason(seasonId))
                .flatMap(tuple -> syncRound(rosterId, matchday, tuple.getT1(), tuple.getT2()))
                .doOnSuccess(result -> log.info(
                        "Sync statistiche reali completata per rosterId={} matchday={}: {} giocatori aggiornati, {} squadre non risolte",
                        rosterId, matchday, result.getUpdatedPlayers().size(), result.getUnresolvedTeams().size()))
                .doOnError(ex -> log.warn("Errore nella sync statistiche reali per rosterId={} matchday={}", rosterId, matchday, ex));
    }

    private Mono<List<PlayerDto>> loadRosterPlayers(RosterDto roster) {
        List<RosterPlayerDto> rosterPlayers = roster.getPlayers() == null ? List.of() : roster.getPlayers();
        return Flux.fromIterable(rosterPlayers)
                .flatMap(rp -> playerDao.getById(rp.getPlayerId()))
                .collectList();
    }

    private Mono<MatchStatsSyncResult> syncRound(String rosterId, Integer matchday, List<PlayerDto> players,
                                                  RoundsResponse roundsResponse) {
        Optional<SmRound> round = selectRound(matchday, roundsResponse);
        Map<String, List<PlayerDto>> byTeam = players.stream()
                .filter(p -> p.getRealTeam() != null)
                .collect(Collectors.groupingBy(PlayerDto::getRealTeam));

        if (round.isEmpty()) {
            log.warn("Nessun round SportMonks trovato per matchday={}: nessuna sincronizzazione possibile", matchday);
            List<String> allTeams = byTeam.keySet().stream().toList();
            return Mono.just(buildResult(List.of(), allTeams));
        }

        List<SmFixture> fixtures = round.get().getFixtures() == null ? List.of() : round.get().getFixtures();

        return Flux.fromIterable(byTeam.entrySet())
                .concatMap(entry -> syncTeam(matchday, entry.getKey(), entry.getValue(), fixtures))
                .collectList()
                .flatMap(outcomes -> {
                    List<PlayerMatchStatUpdate> updated = new ArrayList<>();
                    List<String> unresolved = new ArrayList<>();
                    outcomes.forEach(o -> {
                        updated.addAll(o.updates());
                        if (o.unresolvedTeam() != null) unresolved.add(o.unresolvedTeam());
                    });
                    return matchStatsCsvExporter.export(rosterId)
                            .thenReturn(buildResult(updated, unresolved))
                            .onErrorResume(ex -> {
                                log.warn("Export CSV fallito per rosterId={}, statistiche comunque salvate su DynamoDB", rosterId, ex);
                                return Mono.just(buildResult(updated, unresolved));
                            });
                });
    }

    private Optional<SmRound> selectRound(Integer matchday, RoundsResponse response) {
        List<SmRound> rounds = response == null || response.getData() == null ? List.of() : response.getData();
        String target = String.valueOf(matchday);
        return rounds.stream().filter(r -> target.equals(r.getName())).findFirst();
    }

    private Mono<TeamSyncOutcome> syncTeam(Integer matchday, String realTeam, List<PlayerDto> teamPlayers,
                                           List<SmFixture> fixtures) {
        Optional<SmFixture> fixtureOpt = findFixtureForTeam(realTeam, fixtures);
        if (fixtureOpt.isEmpty()) {
            log.info("Nessuna fixture SportMonks trovata per realTeam={} nella giornata {}", realTeam, matchday);
            return Mono.just(TeamSyncOutcome.unresolved(realTeam));
        }
        SmFixture fixture = fixtureOpt.get();
        Optional<Integer> ownTeamIdOpt = resolveOwnTeamId(realTeam, fixture);
        if (ownTeamIdOpt.isEmpty()) {
            log.warn("Fixture SportMonks trovata per realTeam={} ma team_id non risolto tra i participants", realTeam);
            return Mono.just(TeamSyncOutcome.unresolved(realTeam));
        }
        Integer ownTeamId = ownTeamIdOpt.get();
        List<SmEvent> events = fixture.getEvents() == null ? List.of() : fixture.getEvents();
        int golSubitiSquadra = countGolSubiti(events, ownTeamId);

        return Flux.fromIterable(teamPlayers)
                .flatMap(player -> updatePlayerStats(matchday, realTeam, player, events, golSubitiSquadra))
                .collectList()
                .map(TeamSyncOutcome::matched);
    }

    /**
     * Gol subiti dalla squadra in questa fixture: gol/rigori segnati dall'altra squadra, piu' gli autogol
     * dei propri giocatori (assunzione: SportMonks valorizza team_id dell'evento OwnGoal con la squadra
     * "danneggiata", cioe' la nostra - se si rivelasse errata su un caso reale, e' isolata qui).
     */
    private static int countGolSubiti(List<SmEvent> events, Integer ownTeamId) {
        int golSubiti = 0;
        for (SmEvent ev : events) {
            if (ev.getTypeId() == null || ev.getTeamId() == null) continue;
            boolean isOwnTeamEvent = ownTeamId.equals(ev.getTeamId());
            boolean isGoalOrPenalty = GOAL_TYPE_ID.equals(ev.getTypeId()) || PENALTY_TYPE_ID.equals(ev.getTypeId());
            if (isGoalOrPenalty && !isOwnTeamEvent) {
                golSubiti++;
            } else if (OWN_GOAL_TYPE_ID.equals(ev.getTypeId()) && isOwnTeamEvent) {
                golSubiti++;
            }
        }
        return golSubiti;
    }

    private Optional<SmFixture> findFixtureForTeam(String realTeam, List<SmFixture> fixtures) {
        String normalizedTeam = normalize(realTeam);
        return fixtures.stream()
                .filter(f -> f.getParticipants() != null && f.getParticipants().stream()
                        .anyMatch(p -> matchesTeamName(normalizedTeam, p.getName())))
                .findFirst();
    }

    private Optional<Integer> resolveOwnTeamId(String realTeam, SmFixture fixture) {
        String normalizedTeam = normalize(realTeam);
        if (fixture.getParticipants() == null) return Optional.empty();
        return fixture.getParticipants().stream()
                .filter(p -> matchesTeamName(normalizedTeam, p.getName()))
                .map(SmParticipant::getId)
                .findFirst();
    }

    private Mono<PlayerMatchStatUpdate> updatePlayerStats(Integer matchday, String realTeam, PlayerDto player,
                                                           List<SmEvent> events, int golSubitiSquadra) {
        String normalizedName = normalize(player.getName());

        int gol = 0;
        int assist = 0;
        int ammonizioni = 0;
        int espulsioni = 0;
        for (SmEvent ev : events) {
            if (ev.getTypeId() == null) continue;
            boolean isThisPlayer = matchesPlayerName(normalizedName, ev.getPlayerName());
            boolean isGoalOrPenalty = GOAL_TYPE_ID.equals(ev.getTypeId()) || PENALTY_TYPE_ID.equals(ev.getTypeId());
            if (isGoalOrPenalty && isThisPlayer) {
                gol++;
            }
            if (isGoalOrPenalty && matchesPlayerName(normalizedName, ev.getRelatedPlayerName())) {
                assist++;
            }
            if (YELLOWCARD_TYPE_ID.equals(ev.getTypeId()) && isThisPlayer) {
                ammonizioni++;
            }
            if ((REDCARD_TYPE_ID.equals(ev.getTypeId()) || YELLOWREDCARD_TYPE_ID.equals(ev.getTypeId())) && isThisPlayer) {
                espulsioni++;
            }
        }

        boolean tracksGolSubiti = player.getRole() == Role.POR || player.getRole() == Role.DIF;
        Integer golSubiti = tracksGolSubiti ? golSubitiSquadra : null;
        int golFinal = gol;
        int assistFinal = assist;
        int ammonizioniFinal = ammonizioni;
        int espulsioniFinal = espulsioni;

        return playerMatchStatDao.getByPlayerAndMatchday(player.getPlayerId(), matchday)
                .defaultIfEmpty(PlayerMatchStatDto.builder().playerId(player.getPlayerId()).matchDay(matchday).build())
                .flatMap(existing -> {
                    Double fantavoto = FantaVotoCalculator.calcola(existing.getVoto(), golFinal, assistFinal, golSubiti,
                            ammonizioniFinal, espulsioniFinal);
                    PlayerMatchStatDto toSave = PlayerMatchStatDto.builder()
                            .playerId(player.getPlayerId())
                            .matchDay(matchday)
                            .season(existing.getSeason())
                            .opponentTeam(existing.getOpponentTeam())
                            .home(existing.isHome())
                            .voto(existing.getVoto())
                            .fantavoto(fantavoto)
                            .gol(golFinal)
                            .assist(assistFinal)
                            .ammonizioni(ammonizioniFinal)
                            .espulsioni(espulsioniFinal)
                            .golSubiti(golSubiti)
                            .xg(existing.getXg())
                            .xa(existing.getXa())
                            .build();

                    return playerMatchStatDao.save(toSave)
                            .doOnSuccess(saved -> log.info(
                                    "Statistiche reali sincronizzate per '{}' ({}) giornata {}: gol={} assist={} ammonizioni={} espulsioni={} golSubiti={} fantavoto={}",
                                    player.getName(), player.getPlayerId(), matchday, golFinal, assistFinal,
                                    ammonizioniFinal, espulsioniFinal, golSubiti, fantavoto))
                            .map(saved -> toApiUpdate(player, realTeam, golFinal, assistFinal, ammonizioniFinal,
                                    espulsioniFinal, golSubiti, fantavoto))
                            // Un salvataggio fallito per un singolo giocatore non deve far fallire l'intera sync
                            // delle altre squadre/giocatori: l'errore e' gia' loggato da PlayerMatchStatDao.save.
                            .onErrorResume(ex -> Mono.empty());
                });
    }

    private static PlayerMatchStatUpdate toApiUpdate(PlayerDto player, String realTeam, int gol, int assist,
                                                      int ammonizioni, int espulsioni, Integer golSubiti, Double fantavoto) {
        return PlayerMatchStatUpdate.builder()
                .playerId(player.getPlayerId())
                .name(player.getName())
                .realTeam(realTeam)
                .gol(gol)
                .assist(assist)
                .ammonizioni(ammonizioni)
                .espulsioni(espulsioni)
                .golSubiti(golSubiti)
                .fantavoto(fantavoto)
                .build();
    }

    private static boolean matchesTeamName(String normalizedTarget, String candidateName) {
        if (candidateName == null || normalizedTarget.isEmpty()) return false;
        String candidate = normalize(candidateName);
        if (candidate.isEmpty()) return false;
        return candidate.equals(normalizedTarget) || candidate.contains(normalizedTarget) || normalizedTarget.contains(candidate);
    }

    private static boolean matchesPlayerName(String normalizedTarget, String candidateName) {
        if (candidateName == null || normalizedTarget.isEmpty()) return false;
        String candidate = normalize(candidateName);
        if (candidate.equals(normalizedTarget)) return true;
        if (candidate.length() < 3 || normalizedTarget.length() < 3) return false;
        return candidate.contains(normalizedTarget) || normalizedTarget.contains(candidate);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String noAccents = Normalizer.normalize(value.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase().replaceAll("\\s+", " ");
    }

    private static MatchStatsSyncResult buildResult(List<PlayerMatchStatUpdate> updated, List<String> unresolved) {
        return MatchStatsSyncResult.builder()
                .updatedPlayers(updated)
                .unresolvedTeams(unresolved)
                .build();
    }

    private record TeamSyncOutcome(List<PlayerMatchStatUpdate> updates, String unresolvedTeam) {
        static TeamSyncOutcome matched(List<PlayerMatchStatUpdate> updates) {
            return new TeamSyncOutcome(updates, null);
        }
        static TeamSyncOutcome unresolved(String realTeam) {
            return new TeamSyncOutcome(List.of(), realTeam);
        }
    }
}
