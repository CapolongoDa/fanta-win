package it.capoldan.fantawin.service;

import it.capoldan.fantawin.dto.*;
import it.capoldan.fantawin.exception.DataIntegrityException;
import it.capoldan.fantawin.exception.ExceptionsCodes;
import it.capoldan.fantawin.exception.IdConflictException;
import it.capoldan.fantawin.exception.NotFoundException;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.BulkAddPlayerEntry;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.BulkAddResult;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.BulkAddUnresolvedEntry;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.Player;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.RosterResponse;
import it.capoldan.fantawin.mapper.RosterAggregationMapper;
import it.capoldan.fantawin.mapper.RosterRequestMapper;
import it.capoldan.fantawin.middleware.dao.dynamo.*;
import it.capoldan.fantawin.utils.RoleParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RosterService {

    /** Composizione fissa della rosa fantacalcistica: 3 POR + 8 DIF + 8 CEN + 6 ATT = 25.
     * Se la lega cambia regole, va reso configurabile (es. via FantaWinConfigs) invece
     * che hardcoded qui - per ora è un vincolo fisso della singola lega dell'utente. */
    private static final Map<Role, Integer> ROSTER_SLOTS = Map.of(
            Role.POR, 3,
            Role.DIF, 8,
            Role.CEN, 8,
            Role.ATT, 6
    );

    private final RosterDao rosterDao;
    private final PlayerDao playerDao;
    private final AvailabilityReportDao availabilityReportDao;
    private final FixtureDao fixtureDao;
    private final PlayerMatchStatDao playerMatchStatDao;
    private final PlayerCatalogResolverService playerCatalogResolverService;

    public RosterService(RosterDao rosterDao,
                         PlayerDao playerDao,
                         AvailabilityReportDao availabilityReportDao,
                         FixtureDao fixtureDao,
                         PlayerMatchStatDao playerMatchStatDao,
                         PlayerCatalogResolverService playerCatalogResolverService) {
        this.rosterDao = rosterDao;
        this.playerDao = playerDao;
        this.availabilityReportDao = availabilityReportDao;
        this.fixtureDao = fixtureDao;
        this.playerMatchStatDao = playerMatchStatDao;
        this.playerCatalogResolverService = playerCatalogResolverService;
    }

    public Mono<RosterResponse> getRoster(Integer matchday, String rosterId) {
        log.info("Recupero roster rosterId={} matchday={}", rosterId, matchday);
        return rosterDao.getById(rosterId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Rosa {} non trovata durante getRoster", rosterId);
                    return Mono.error(new NotFoundException(
                            "Rosa " + rosterId + " non trovata", ExceptionsCodes.ERROR_CODE_NOT_FOUND));
                }))
                .zipWith(fixturesForMatchday(matchday))
                .flatMap(tuple -> {
                    RosterDto roster = tuple.getT1();
                    Map<String, FixtureDto> fixturesByTeam = tuple.getT2();
                    return Flux.fromIterable(safePlayers(roster))
                            .flatMap(entry -> buildApiPlayer(entry.getPlayerId(), fixturesByTeam))
                            .collectList()
                            .map(players -> groupByPosition(roster.getTeamName(), players));
                })
                .doOnSuccess(response -> log.info("Roster rosterId={} recuperato: {} POR, {} DIF, {} CEN, {} ATT",
                        rosterId,
                        response.getGoalkeepers().size(), response.getDefenders().size(),
                        response.getMidfielders().size(), response.getForwards().size()))
                .doOnError(ex -> log.warn("Errore nel recupero del roster rosterId={}", rosterId, ex));
    }

    /** Un'unica query sulla partizione "matchday" per l'intera rosa (PK della tabella Fixtures),
     * invece di una query ripetuta per ciascun giocatore filtrata poi in memoria: elimina fino a
     * N query ridondanti (N = numero giocatori in rosa) per ogni chiamata a getRoster. */
    private Mono<Map<String, FixtureDto>> fixturesForMatchday(Integer matchday) {
        if (matchday == null) {
            return Mono.just(Map.of());
        }
        return fixtureDao.findByMatchday(matchday)
                .collectMap(FixtureDto::getRealTeam, Function.identity());
    }

    public Mono<Player> addOrUpdatePlayer(Player request, String rosterId) {
        Role role = RosterRequestMapper.toRole(request);
        return addOrUpdatePlayerCore(request.getId(), request.getName(), request.getRealTeam(), role, rosterId);
    }

    /**
     * Aggiunge in blocco una lista di giocatori (nome + ruolo, come li scrive l'utente) alla rosa,
     * risolvendo ciascuno contro l'anagrafica PlayerCatalog (PlayerCatalogResolverService) - l'id
     * interno non viene mai fornito dall'utente, e' sempre ricavato dalla query sul catalogo.
     * Una entry non risolvibile (ruolo non riconosciuto, nessun match, o nome ambiguo) NON blocca le
     * altre: viene riportata in "unresolved" con il motivo, stesso principio di
     * PlayerMatchStatImportService/PlayerCatalogImportService (mai fallire l'intero batch per una riga).
     *
     * Le entry vengono elaborate in sequenza (concatMap, non flatMap): ogni addOrUpdatePlayerCore
     * legge e riscrive l'intero Roster, quindi due aggiunte dello stesso ruolo in parallelo
     * leggerebbero lo stesso stato "vecchio" del roster e potrebbero entrambe superare il controllo
     * di capienza (ROSTER_SLOTS) nonostante lo slot sia in realta' unico - la sequenzialita' e'
     * necessaria perche' ogni controllo di capienza veda l'esito delle aggiunte precedenti.
     */
    public Mono<BulkAddResult> addPlayersBulk(List<BulkAddPlayerEntry> entries, String rosterId) {
        log.info("Bulk-add di {} giocatori in rosterId={}", entries == null ? 0 : entries.size(), rosterId);
        List<BulkAddPlayerEntry> safeEntries = entries == null ? List.of() : entries;
        return Flux.fromIterable(safeEntries)
                .concatMap(entry -> resolveAndAddOne(entry, rosterId))
                .collectList()
                .map(RosterService::buildBulkAddResult)
                .doOnSuccess(result -> log.info("Bulk-add completato per rosterId={}: {} aggiunti, {} non risolti",
                        rosterId, result.getAdded().size(), result.getUnresolved().size()));
    }

    private Mono<BulkAddOutcome> resolveAndAddOne(BulkAddPlayerEntry entry, String rosterId) {
        Role role = RoleParser.parse(entry.getRuolo());
        if (role == null) {
            log.warn("Bulk-add rosterId={}: ruolo non riconosciuto '{}' per nome='{}'", rosterId, entry.getRuolo(), entry.getNome());
            return Mono.just(BulkAddOutcome.unresolved(entry.getNome(), entry.getRuolo(),
                    "ruolo non riconosciuto: '" + entry.getRuolo() + "' (attesi P/D/C/A o POR/DIF/CEN/ATT)"));
        }
        return playerCatalogResolverService.resolve(entry.getNome(), role)
                .flatMap(resolution -> {
                    if (resolution.isResolved()) {
                        PlayerCatalogDto catalogEntry = resolution.match();
                        return addOrUpdatePlayerCore(catalogEntry.getCatalogId(), catalogEntry.getNome(),
                                catalogEntry.getSquadra(), role, rosterId)
                                .map(BulkAddOutcome::added)
                                .onErrorResume(ex -> {
                                    log.warn("Bulk-add rosterId={}: aggiunta fallita per catalogId={} ({})",
                                            rosterId, catalogEntry.getCatalogId(), entry.getNome(), ex);
                                    return Mono.just(BulkAddOutcome.unresolved(entry.getNome(), entry.getRuolo(),
                                            "errore nell'aggiunta: " + ex.getMessage()));
                                });
                    }
                    if (resolution.isAmbiguous()) {
                        String candidateNames = resolution.candidates().stream()
                                .map(c -> c.getNome() + " (" + c.getSquadra() + ")")
                                .collect(Collectors.joining(", "));
                        log.warn("Bulk-add rosterId={}: nome ambiguo '{}' ruolo={} - candidati: {}",
                                rosterId, entry.getNome(), role, candidateNames);
                        return Mono.just(BulkAddOutcome.unresolved(entry.getNome(), entry.getRuolo(),
                                "nome ambiguo, possibili corrispondenze: " + candidateNames
                                        + " - ripeti specificando il nome esatto come in anagrafica"));
                    }
                    return Mono.just(BulkAddOutcome.unresolved(entry.getNome(), entry.getRuolo(),
                            "nessun giocatore trovato in anagrafica per ruolo " + role));
                });
    }

    /** Esito dell'elaborazione di una singola entry di bulk-add: o un Player aggiunto, o una entry non risolta (mai entrambi). */
    private record BulkAddOutcome(Player added, BulkAddUnresolvedEntry unresolved) {
        static BulkAddOutcome added(Player player) {
            return new BulkAddOutcome(player, null);
        }

        static BulkAddOutcome unresolved(String nome, String ruolo, String motivo) {
            return new BulkAddOutcome(null, BulkAddUnresolvedEntry.builder()
                    .nome(nome)
                    .ruolo(ruolo)
                    .motivo(motivo)
                    .build());
        }
    }

    private static BulkAddResult buildBulkAddResult(List<BulkAddOutcome> outcomes) {
        List<Player> added = outcomes.stream().map(BulkAddOutcome::added).filter(Objects::nonNull).toList();
        List<BulkAddUnresolvedEntry> unresolved = outcomes.stream().map(BulkAddOutcome::unresolved).filter(Objects::nonNull).toList();
        return BulkAddResult.builder().added(added).unresolved(unresolved).build();
    }

    /** Logica condivisa di aggiunta/aggiornamento di un giocatore in rosa (cap ROSTER_SLOTS, versioning
     * ottimistico su Players): usata sia da addOrUpdatePlayer (chiamata API diretta con Player completo)
     * sia da addPlayersBulk (giocatore risolto dal catalogo) - un'unica implementazione del controllo di
     * capienza per ruolo, non duplicata tra i due flussi. */
    private Mono<Player> addOrUpdatePlayerCore(String playerId, String name, String realTeam, Role role, String rosterId) {
        log.info("Aggiungo/aggiorno giocatore id={} ruolo={} in rosterId={}", playerId, role, rosterId);

        return rosterDao.getById(rosterId)
                .defaultIfEmpty(RosterDto.builder().rosterId(rosterId).players(new ArrayList<>()).build())
                .flatMap(roster -> {
                    List<RosterPlayerDto> players = new ArrayList<>(safePlayers(roster));

                    // Esclude il giocatore stesso: un update non deve essere bloccato dal proprio slot.
                    int cap = ROSTER_SLOTS.getOrDefault(role, 0);
                    long countOthers = players.stream()
                            .filter(p -> p.getFantasyRole() == role)
                            .filter(p -> !p.getPlayerId().equals(playerId))
                            .count();
                    if (countOthers >= cap) {
                        log.warn("Rifiuto aggiunta giocatore id={} a rosterId={}: ruolo {} gia' al completo ({}/{})",
                                playerId, rosterId, role, cap, cap);
                        return Mono.error(new IdConflictException(
                                ExceptionsCodes.ERROR_CODE_GENERIC_INVALIDPARAMETER_DUPLICATED,
                                Map.of("fantasyRole", role.name() + " già al completo (" + cap + "/" + cap + ")")));
                    }

                    players.removeIf(p -> p.getPlayerId().equals(playerId));
                    players.add(RosterPlayerDto.builder().playerId(playerId).fantasyRole(role).build());
                    roster.setPlayers(players);

                    return playerDao.getById(playerId)
                            .map(PlayerDto::getVersion)
                            .map(Optional::of)
                            .defaultIfEmpty(Optional.<Long>empty())
                            .flatMap(existingVersion -> {
                                PlayerDto playerDto = PlayerDto.builder()
                                        .playerId(playerId)
                                        .name(name)
                                        .realTeam(realTeam)
                                        .role(role)
                                        .active(true)
                                        .version(existingVersion.orElse(null))
                                        .build();

                                return playerDao.save(playerDto)
                                        .then(rosterDao.save(roster))
                                        .then(buildApiPlayer(playerId, Map.<String, FixtureDto>of()));
                            });
                })
                .doOnSuccess(player -> log.info("Giocatore id={} salvato in rosterId={}", playerId, rosterId))
                .doOnError(ex -> log.warn("Errore nell'aggiunta/aggiornamento del giocatore id={} in rosterId={}", playerId, rosterId, ex));
    }

    public Mono<Void> deletePlayer(String playerId, String rosterId) {
        log.info("Rimuovo giocatore id={} da rosterId={}", playerId, rosterId);
        // Rimuove solo l'associazione alla rosa, non l'anagrafica del giocatore.
        return rosterDao.getById(rosterId)
                .flatMap(roster -> {
                    List<RosterPlayerDto> players = new ArrayList<>(safePlayers(roster));
                    if (!players.removeIf(p -> p.getPlayerId().equals(playerId))) {
                        log.warn("Giocatore id={} non presente in rosterId={}: nessuna rimozione effettuata", playerId, rosterId);
                        return Mono.empty();
                    }
                    roster.setPlayers(players);
                    return rosterDao.save(roster);
                })
                .then()
                .doOnSuccess(v -> log.info("Giocatore id={} rimosso da rosterId={}", playerId, rosterId))
                .doOnError(ex -> log.warn("Errore nella rimozione del giocatore id={} da rosterId={}", playerId, rosterId, ex));
    }

    private static List<RosterPlayerDto> safePlayers(RosterDto roster) {
        return roster.getPlayers() == null ? List.of() : roster.getPlayers();
    }

    private Mono<Player> buildApiPlayer(String playerId, Map<String, FixtureDto> fixturesByTeam) {
        // Il giocatore e' referenziato dalla rosa ma assente dall'anagrafica: incoerenza tra
        // tabelle DynamoDB, non un generico errore interno - va segnalata come tale.
        Mono<PlayerDto> playerMono = playerDao.getById(playerId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Incoerenza dati: giocatore id={} presente in rosa ma assente dal registry Players", playerId);
                    return Mono.error(new DataIntegrityException(
                            "Player " + playerId + " presente in rosa ma non trovato nel registry"));
                }));

        Mono<AvailabilityReportDto> availabilityMono = availabilityReportDao.getById(playerId)
                .defaultIfEmpty(AvailabilityReportDto.builder()
                        .playerId(playerId)
                        .status(AvailabilityStatus.OK)
                        .startingProbability(100.0)
                        .build());

        Mono<List<PlayerMatchStatDto>> recentStatsMono = playerMatchStatDao.findByPlayer(playerId).collectList();

        return Mono.zip(playerMono, availabilityMono, recentStatsMono)
                .map(tuple -> {
                    PlayerDto player = tuple.getT1();
                    AvailabilityReportDto availability = tuple.getT2();
                    List<PlayerMatchStatDto> recentStats = tuple.getT3();
                    FixtureDto fixture = fixturesByTeam.get(player.getRealTeam());

                    return RosterAggregationMapper.toApiPlayer(
                            player,
                            availability.getStatus(),
                            availability.getStartingProbability(),
                            fixture,
                            recentStats);
                });
    }

    private RosterResponse groupByPosition(String teamName, List<Player> players) {
        // Un solo passaggio sulla lista (Collectors.groupingBy) invece di 4 scan separati,
        // uno per ruolo, sull'intera lista dei giocatori.
        Map<Player.PositionEnum, List<Player>> byPosition = players.stream()
                .collect(Collectors.groupingBy(Player::getPosition));

        RosterResponse response = new RosterResponse();
        response.setTeamName(teamName);
        response.setGoalkeepers(byPosition.getOrDefault(Player.PositionEnum.POR, List.of()));
        response.setDefenders(byPosition.getOrDefault(Player.PositionEnum.DIF, List.of()));
        response.setMidfielders(byPosition.getOrDefault(Player.PositionEnum.CEN, List.of()));
        response.setForwards(byPosition.getOrDefault(Player.PositionEnum.ATT, List.of()));
        return response;
    }
}
