package it.capoldan.fantawin.service;

import it.capoldan.fantawin.config.FantaWinConfigs;
import it.capoldan.fantawin.dto.PlayerDto;
import it.capoldan.fantawin.dto.PlayerMatchStatDto;
import it.capoldan.fantawin.dto.RosterPlayerDto;
import it.capoldan.fantawin.middleware.dao.dynamo.PlayerDao;
import it.capoldan.fantawin.middleware.dao.dynamo.PlayerMatchStatDao;
import it.capoldan.fantawin.middleware.dao.dynamo.RosterDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Esporta lo storico corrente di PlayerMatchStat di un roster in un CSV leggibile, uno per rosterId
 * (utile con piu' leghe/squadre diverse), nello stesso formato di import atteso da
 * {@link PlayerMatchStatImportService} - non e' la fonte di verita' (quella e' DynamoDB), solo uno
 * snapshot comodo da aprire/condividere, riscritto ad ogni sync di {@link RealMatchStatsSyncService}.
 */
@Slf4j
@Component
public class MatchStatsCsvExporter {

    private static final String HEADER =
            "giocatore;squadra;giornata;stagione;avversario;casa;voto;fantavoto;gol;assist;ammonizioni;espulsioni;xg;xa;golsubiti";

    private final RosterDao rosterDao;
    private final PlayerDao playerDao;
    private final PlayerMatchStatDao playerMatchStatDao;
    private final FantaWinConfigs fantaWinConfigs;

    public MatchStatsCsvExporter(RosterDao rosterDao, PlayerDao playerDao, PlayerMatchStatDao playerMatchStatDao,
                                 FantaWinConfigs fantaWinConfigs) {
        this.rosterDao = rosterDao;
        this.playerDao = playerDao;
        this.playerMatchStatDao = playerMatchStatDao;
        this.fantaWinConfigs = fantaWinConfigs;
    }

    /** Scrive/sovrascrive {fantawin.match-stats-export-dir}/player-match-stats-{rosterId}.csv. No-op se non configurato. */
    public Mono<Void> export(String rosterId) {
        String dir = fantaWinConfigs.getMatchStatsExportDir();
        if (dir == null || dir.isBlank()) {
            log.info("fantawin.match-stats-export-dir non configurato: export CSV per rosterId={} saltato", rosterId);
            return Mono.empty();
        }
        return rosterDao.getById(rosterId)
                .flatMap(roster -> {
                    List<RosterPlayerDto> rosterPlayers = roster.getPlayers() == null ? List.of() : roster.getPlayers();
                    return Flux.fromIterable(rosterPlayers)
                            .flatMap(rp -> playerDao.getById(rp.getPlayerId()))
                            .collectList()
                            .flatMap(players -> Flux.fromIterable(players)
                                    .flatMap(player -> playerMatchStatDao.findByPlayer(player.getPlayerId())
                                            .map(stat -> toCsvRow(player, stat)))
                                    .collectList()
                                    .flatMap(rows -> writeFile(dir, rosterId, rows)));
                })
                .doOnError(ex -> log.warn("Export CSV fallito per rosterId={}", rosterId, ex));
    }

    private static String toCsvRow(PlayerDto player, PlayerMatchStatDto s) {
        return String.join(";",
                nullToEmpty(player.getName()),
                nullToEmpty(player.getRealTeam()),
                s.getMatchDay() == null ? "" : String.valueOf(s.getMatchDay()),
                nullToEmpty(s.getSeason()),
                nullToEmpty(s.getOpponentTeam()),
                s.isHome() ? "si" : "no",
                s.getVoto() == null ? "" : String.valueOf(s.getVoto()),
                s.getFantavoto() == null ? "" : String.valueOf(s.getFantavoto()),
                s.getGol() == null ? "" : String.valueOf(s.getGol()),
                s.getAssist() == null ? "" : String.valueOf(s.getAssist()),
                s.getAmmonizioni() == null ? "" : String.valueOf(s.getAmmonizioni()),
                s.getEspulsioni() == null ? "" : String.valueOf(s.getEspulsioni()),
                s.getXg() == null ? "" : String.valueOf(s.getXg()),
                s.getXa() == null ? "" : String.valueOf(s.getXa()),
                s.getGolSubiti() == null ? "" : String.valueOf(s.getGolSubiti()));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private Mono<Void> writeFile(String dir, String rosterId, List<String> rows) {
        return Mono.<Void>fromRunnable(() -> {
                    try {
                        Path dirPath = Paths.get(dir);
                        Files.createDirectories(dirPath);
                        Path file = dirPath.resolve("player-match-stats-" + rosterId + ".csv");
                        List<String> lines = new ArrayList<>();
                        lines.add(HEADER);
                        lines.addAll(rows);
                        Files.write(file, lines, StandardCharsets.UTF_8);
                        log.info("Export CSV scritto: {} ({} righe)", file, rows.size());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
