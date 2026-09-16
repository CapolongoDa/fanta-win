package it.capoldan.fantawin.service;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import it.capoldan.fantawin.dto.PlayerDto;
import it.capoldan.fantawin.dto.PlayerMatchStatDto;
import it.capoldan.fantawin.exception.InvalidImportFileException;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.MatchStatsImportResult;
import it.capoldan.fantawin.middleware.dao.dynamo.PlayerDao;
import it.capoldan.fantawin.middleware.dao.dynamo.PlayerMatchStatDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Import da file CSV delle statistiche di giornata (voto/fantavoto/bonus/malus) dei calciatori.
 * Football-Data.org non fornisce questo dato (e' specifico del fantacalcio italiano, non delle
 * statistiche calcistiche generiche): l'unica fonte percorribile senza integrare uno scraper e'
 * un file preparato/esportato manualmente dall'utente da dove consulta i voti (es. sito fantacalcio,
 * copia-incolla in un foglio di calcolo).
 *
 * Formato atteso: CSV separato da ';', UTF-8, con intestazione sulla prima riga:
 * giocatore;squadra;giornata;stagione;avversario;casa;voto;fantavoto;gol;assist;ammonizioni;espulsioni;xg;xa
 * Solo giocatore, squadra e giornata sono obbligatori; gli altri campi possono essere vuoti.
 * Il giocatore viene risolto incrociando nome+squadra reale con l'anagrafica (PlayerDao): una riga
 * il cui giocatore non viene trovato non blocca l'intero import, ma viene riportata in "errors".
 */
@Slf4j
@Service
public class PlayerMatchStatImportService {

    private static final int EXPECTED_COLUMNS = 14;

    private final PlayerDao playerDao;
    private final PlayerMatchStatDao playerMatchStatDao;

    public PlayerMatchStatImportService(PlayerDao playerDao, PlayerMatchStatDao playerMatchStatDao) {
        this.playerDao = playerDao;
        this.playerMatchStatDao = playerMatchStatDao;
    }

    public Mono<MatchStatsImportResult> importFromCsv(Flux<Part> fileParts) {
        log.info("Avvio import CSV delle statistiche di giornata");
        return extractCsvContent(fileParts)
                .zipWith(playerDao.findAll()
                        .collectList()
                        .map(PlayerMatchStatImportService::buildPlayerLookup))
                .flatMap(tuple -> parseAndSave(tuple.getT1(), tuple.getT2()))
                .doOnSuccess(result -> log.info("Import CSV completato: {} righe importate, {} scartate",
                        result.getImported(), result.getSkipped()))
                .doOnError(ex -> log.warn("Errore durante l'import del CSV delle statistiche", ex));
    }

    private Mono<String> extractCsvContent(Flux<Part> fileParts) {
        return fileParts
                .filter(FilePart.class::isInstance)
                .cast(FilePart.class)
                .next()
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Import CSV rifiutato: nessun file nel campo 'file' della richiesta multipart");
                    return Mono.error(new InvalidImportFileException(
                            "Nessun file ricevuto nel campo 'file' della richiesta multipart"));
                }))
                .flatMap(filePart -> DataBufferUtils.join(filePart.content())
                        .map(PlayerMatchStatImportService::readAndRelease)
                        .switchIfEmpty(Mono.defer(() -> {
                            log.warn("Import CSV rifiutato: il file caricato ({}) e' vuoto", filePart.filename());
                            return Mono.error(new InvalidImportFileException(
                                    "Il file caricato (" + filePart.filename() + ") e' vuoto"));
                        })));
    }

    private static String readAndRelease(DataBuffer dataBuffer) {
        byte[] bytes = new byte[dataBuffer.readableByteCount()];
        dataBuffer.read(bytes);
        DataBufferUtils.release(dataBuffer);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** playerId per chiave "nome normalizzato|squadra normalizzata" */
    private static Map<String, String> buildPlayerLookup(List<PlayerDto> players) {
        Map<String, String> lookup = new HashMap<>();
        for (PlayerDto player : players) {
            lookup.put(normalize(player.getName()) + "|" + normalize(player.getRealTeam()), player.getPlayerId());
        }
        return lookup;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String noAccents = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccents.toLowerCase().replaceAll("\\s+", " ");
    }

    private Mono<MatchStatsImportResult> parseAndSave(String csvContent, Map<String, String> playerLookup) {
        return Mono.fromCallable(() -> parseRows(csvContent, playerLookup))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(parsed -> {
                    if (!parsed.errors().isEmpty()) {
                        log.warn("Import CSV: {} righe scartate per errori di parsing/risoluzione giocatore", parsed.errors().size());
                    }
                    return Flux.fromIterable(parsed.dtos())
                            .flatMap(playerMatchStatDao::save)
                            .count()
                            .map(savedCount -> MatchStatsImportResult.builder()
                                    .imported(savedCount.intValue())
                                    .skipped(parsed.errors().size())
                                    .errors(parsed.errors())
                                    .build());
                });
    }

    private ParsedRows parseRows(String csvContent, Map<String, String> playerLookup) {
        List<PlayerMatchStatDto> dtos = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (CSVReader reader = new CSVReaderBuilder(new StringReader(csvContent))
                .withCSVParser(new CSVParserBuilder().withSeparator(';').build())
                .build()) {

            List<String[]> allRows = reader.readAll();
            if (allRows.isEmpty()) {
                log.warn("Import CSV rifiutato: il file non contiene nemmeno la riga di intestazione");
                throw new InvalidImportFileException("Il file CSV non contiene nemmeno la riga di intestazione");
            }
            log.info("Import CSV: {} righe di dati da elaborare (esclusa intestazione)", allRows.size() - 1);

            // riga 1 = intestazione, righe dati numerate a partire da 2 (coerenti con un editor/Excel)
            for (int i = 1; i < allRows.size(); i++) {
                int rowNumber = i + 1;
                String[] row = allRows.get(i);
                if (row.length == 1 && row[0].isBlank()) continue; // riga vuota, ignorata silenziosamente

                try {
                    dtos.add(parseRow(row, rowNumber, playerLookup));
                } catch (RowParseException e) {
                    log.warn("Import CSV: riga {} scartata: {}", rowNumber, e.getMessage());
                    errors.add("riga " + rowNumber + ": " + e.getMessage());
                }
            }
        } catch (java.io.IOException | com.opencsv.exceptions.CsvException e) {
            log.warn("Import CSV rifiutato: file non leggibile come CSV", e);
            throw new InvalidImportFileException("File non leggibile come CSV: " + e.getMessage(), e);
        }

        return new ParsedRows(dtos, errors);
    }

    private PlayerMatchStatDto parseRow(String[] row, int rowNumber, Map<String, String> playerLookup) {
        if (row.length < 3) {
            throw new RowParseException("attese almeno le colonne giocatore;squadra;giornata, trovate " + row.length);
        }

        String playerName = col(row, 0);
        String realTeam = col(row, 1);
        String matchdayRaw = col(row, 2);

        if (playerName.isEmpty() || realTeam.isEmpty() || matchdayRaw.isEmpty()) {
            throw new RowParseException("giocatore, squadra e giornata sono obbligatori");
        }

        String playerId = playerLookup.get(normalize(playerName) + "|" + normalize(realTeam));
        if (playerId == null) {
            throw new RowParseException("nessun giocatore trovato per '" + playerName + "' (" + realTeam + ")");
        }

        Integer matchday = parseInt(matchdayRaw, "giornata");

        return PlayerMatchStatDto.builder()
                .playerId(playerId)
                .matchDay(matchday)
                .season(colOrNull(row, 3))
                .opponentTeam(colOrNull(row, 4))
                .home(parseBoolean(colOrNull(row, 5)))
                .voto(parseDouble(colOrNull(row, 6), "voto"))
                .fantavoto(parseDouble(colOrNull(row, 7), "fantavoto"))
                .gol(parseInt(colOrNull(row, 8), "gol"))
                .assist(parseInt(colOrNull(row, 9), "assist"))
                .ammonizioni(parseInt(colOrNull(row, 10), "ammonizioni"))
                .espulsioni(parseInt(colOrNull(row, 11), "espulsioni"))
                .xg(parseDouble(colOrNull(row, 12), "xg"))
                .xa(parseDouble(colOrNull(row, 13), "xa"))
                .build();
    }

    private static String col(String[] row, int idx) {
        return idx < row.length && row[idx] != null ? row[idx].trim() : "";
    }

    private static String colOrNull(String[] row, int idx) {
        String value = col(row, idx);
        return value.isEmpty() ? null : value;
    }

    private static Integer parseInt(String raw, String fieldName) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new RowParseException("valore non numerico per '" + fieldName + "': '" + raw + "'");
        }
    }

    private static Double parseDouble(String raw, String fieldName) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            // tollera sia "6.5" che "6,5" (decimale italiano, tipico di export da Excel)
            return Double.valueOf(raw.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new RowParseException("valore non numerico per '" + fieldName + "': '" + raw + "'");
        }
    }

    private static boolean parseBoolean(String raw) {
        if (raw == null) return false;
        String v = raw.trim().toLowerCase();
        return v.equals("1") || v.equals("true") || v.equals("si") || v.equals("sì") || v.equals("x");
    }

    private record ParsedRows(List<PlayerMatchStatDto> dtos, List<String> errors) {
    }

    /** Eccezione interna di parsing riga, mai propagata all'esterno: viene sempre catturata e trasformata in una entry di errors. */
    private static class RowParseException extends java.lang.RuntimeException {
        RowParseException(String message) {
            super(message);
        }
    }
}
