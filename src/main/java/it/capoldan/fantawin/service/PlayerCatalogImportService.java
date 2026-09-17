package it.capoldan.fantawin.service;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import it.capoldan.fantawin.dto.PlayerCatalogDto;
import it.capoldan.fantawin.dto.Role;
import it.capoldan.fantawin.exception.InvalidImportFileException;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.PlayerCatalogImportResult;
import it.capoldan.fantawin.middleware.dao.dynamo.PlayerCatalogDao;
import it.capoldan.fantawin.utils.RoleParser;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Import da CSV dell'anagrafica completa di Serie A (quotazioni fantacalcio.it) nella tabella
 * PlayerCatalog, usata da PlayerCatalogResolverService per risolvere nome+ruolo -> playerId quando
 * si aggiunge un giocatore alla rosa. Da rilanciare ad ogni inizio stagione (nuove quotazioni) o
 * quando fantacalcio.it aggiorna l'elenco (es. calciomercato in corso).
 *
 * Formato atteso: CSV separato da ';', UTF-8, intestazione sulla prima riga:
 * id;nome;ruolo;squadra
 * Tutte le colonne sono obbligatorie. "ruolo" accetta la sigla P/D/C/A, POR/DIF/CEN/ATT o alias Mantra come CC/DC
 * (vedi RoleParser) - un valore non riconosciuto scarta la riga senza bloccare l'intero import.
 * Ogni riga sovrascrive l'eventuale voce esistente con lo stesso id (nessuna cancellazione preventiva:
 * un id assente dal nuovo CSV resta semplicemente invariato in tabella).
 */
@Slf4j
@Service
public class PlayerCatalogImportService {

    private final PlayerCatalogDao playerCatalogDao;

    public PlayerCatalogImportService(PlayerCatalogDao playerCatalogDao) {
        this.playerCatalogDao = playerCatalogDao;
    }

    public Mono<PlayerCatalogImportResult> importFromCsv(Flux<Part> fileParts) {
        log.info("Avvio import CSV dell'anagrafica PlayerCatalog");
        return extractCsvContent(fileParts)
                .flatMap(this::parseAndSave)
                .doOnSuccess(result -> log.info("Import CSV PlayerCatalog completato: {} righe importate, {} scartate",
                        result.getImported(), result.getSkipped()))
                .doOnError(ex -> log.warn("Errore durante l'import del CSV PlayerCatalog", ex));
    }

    private Mono<String> extractCsvContent(Flux<Part> fileParts) {
        return fileParts
                .filter(FilePart.class::isInstance)
                .cast(FilePart.class)
                .next()
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Import CSV PlayerCatalog rifiutato: nessun file nel campo 'file' della richiesta multipart");
                    return Mono.error(new InvalidImportFileException(
                            "Nessun file ricevuto nel campo 'file' della richiesta multipart"));
                }))
                .flatMap(filePart -> DataBufferUtils.join(filePart.content())
                        .map(PlayerCatalogImportService::readAndRelease)
                        .switchIfEmpty(Mono.defer(() -> {
                            log.warn("Import CSV PlayerCatalog rifiutato: il file caricato ({}) e' vuoto", filePart.filename());
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

    private Mono<PlayerCatalogImportResult> parseAndSave(String csvContent) {
        return Mono.fromCallable(() -> parseRows(csvContent))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(parsed -> {
                    if (!parsed.errors().isEmpty()) {
                        log.warn("Import CSV PlayerCatalog: {} righe scartate per errori di parsing", parsed.errors().size());
                    }
                    return Flux.fromIterable(parsed.dtos())
                            .flatMap(playerCatalogDao::save)
                            .count()
                            .map(savedCount -> PlayerCatalogImportResult.builder()
                                    .imported(savedCount.intValue())
                                    .skipped(parsed.errors().size())
                                    .errors(parsed.errors())
                                    .build());
                });
    }

    private ParsedRows parseRows(String csvContent) {
        List<PlayerCatalogDto> dtos = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (CSVReader reader = new CSVReaderBuilder(new StringReader(csvContent))
                .withCSVParser(new CSVParserBuilder().withSeparator(';').build())
                .build()) {

            List<String[]> allRows = reader.readAll();
            if (allRows.isEmpty()) {
                log.warn("Import CSV PlayerCatalog rifiutato: il file non contiene nemmeno la riga di intestazione");
                throw new InvalidImportFileException("Il file CSV non contiene nemmeno la riga di intestazione");
            }
            log.info("Import CSV PlayerCatalog: {} righe di dati da elaborare (esclusa intestazione)", allRows.size() - 1);

            for (int i = 1; i < allRows.size(); i++) {
                int rowNumber = i + 1;
                String[] row = allRows.get(i);
                if (row.length == 1 && row[0].isBlank()) continue;

                try {
                    dtos.add(parseRow(row, rowNumber));
                } catch (RowParseException e) {
                    log.warn("Import CSV PlayerCatalog: riga {} scartata: {}", rowNumber, e.getMessage());
                    errors.add("riga " + rowNumber + ": " + e.getMessage());
                }
            }
        } catch (java.io.IOException | com.opencsv.exceptions.CsvException e) {
            log.warn("Import CSV PlayerCatalog rifiutato: file non leggibile come CSV", e);
            throw new InvalidImportFileException("File non leggibile come CSV: " + e.getMessage(), e);
        }

        return new ParsedRows(dtos, errors);
    }

    private PlayerCatalogDto parseRow(String[] row, int rowNumber) {
        if (row.length < 4) {
            throw new RowParseException("attese le colonne id;nome;ruolo;squadra, trovate " + row.length);
        }

        String id = col(row, 0);
        String nome = col(row, 1);
        String ruoloRaw = col(row, 2);
        String squadra = col(row, 3);

        if (id.isEmpty() || nome.isEmpty() || ruoloRaw.isEmpty() || squadra.isEmpty()) {
            throw new RowParseException("id, nome, ruolo e squadra sono tutti obbligatori");
        }

        Role ruolo = RoleParser.parse(ruoloRaw);
        if (ruolo == null) {
            throw new RowParseException("ruolo non riconosciuto: '" + ruoloRaw + "' (attesi P/D/C/A, POR/DIF/CEN/ATT o CC/DC)");
        }

        return PlayerCatalogDto.builder()
                .catalogId(id)
                .nome(nome)
                .ruolo(ruolo)
                .squadra(squadra)
                .build();
    }

    private static String col(String[] row, int idx) {
        return idx < row.length && row[idx] != null ? row[idx].trim() : "";
    }

    private record ParsedRows(List<PlayerCatalogDto> dtos, List<String> errors) {
    }

    /** Eccezione interna di parsing riga, mai propagata all'esterno: viene sempre catturata e trasformata in una entry di errors. */
    private static class RowParseException extends java.lang.RuntimeException {
        RowParseException(String message) {
            super(message);
        }
    }
}
