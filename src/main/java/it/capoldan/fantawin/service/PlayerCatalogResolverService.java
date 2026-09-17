package it.capoldan.fantawin.service;

import it.capoldan.fantawin.dto.PlayerCatalogDto;
import it.capoldan.fantawin.dto.Role;
import it.capoldan.fantawin.middleware.dao.dynamo.PlayerCatalogDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.text.Normalizer;
import java.util.List;

/**
 * Risolve un giocatore inserito dall'utente per nome (in chiaro, come lo scrive lui, es. "Oyono" o
 * "Martinez Jo.") e ruolo contro l'anagrafica completa PlayerCatalog, per ricavare l'id univoco da
 * usare in Players/Roster - l'utente non inventa/scrive mai l'id (vedi RosterService#addPlayersBulk).
 *
 * Match esatto (nome normalizzato, stesso ruolo) prima; se non trovato, fallback a contenimento in
 * entrambe le direzioni (utile se l'utente omette il suffisso disambiguante di fantacalcio.it, es.
 * "Konè" invece di "Konè M."). Se il fallback produce PIU' di un candidato, non tenta di indovinare:
 * ritorna tutti i candidati cosi' il chiamante puo' segnalarli e far correggere l'input (es. chiedere
 * di ripetere con "Konè M." invece di "Konè").
 */
@Slf4j
@Service
public class PlayerCatalogResolverService {

    private static final int MIN_SUBSTRING_LENGTH = 3;

    private final PlayerCatalogDao playerCatalogDao;

    public PlayerCatalogResolverService(PlayerCatalogDao playerCatalogDao) {
        this.playerCatalogDao = playerCatalogDao;
    }

    /** Esito della risoluzione: esattamente un candidato (match), nessuno, oppure piu' di uno (ambiguo, in candidates). */
    public record ResolutionResult(PlayerCatalogDto match, List<PlayerCatalogDto> candidates) {
        public boolean isResolved() {
            return match != null;
        }

        public boolean isAmbiguous() {
            return match == null && !candidates.isEmpty();
        }
    }

    public Mono<ResolutionResult> resolve(String nome, Role ruolo) {
        String normalizedTarget = normalize(nome);
        return playerCatalogDao.findAll()
                .filter(entry -> entry.getRuolo() == ruolo)
                .collectList()
                .map(sameRoleEntries -> resolveAmong(normalizedTarget, sameRoleEntries));
    }

    private ResolutionResult resolveAmong(String normalizedTarget, List<PlayerCatalogDto> sameRoleEntries) {
        List<PlayerCatalogDto> exact = sameRoleEntries.stream()
                .filter(entry -> normalize(entry.getNome()).equals(normalizedTarget))
                .toList();
        if (exact.size() == 1) {
            return new ResolutionResult(exact.get(0), List.of());
        }
        if (exact.size() > 1) {
            // Stesso nome normalizzato E stesso ruolo per piu' di un giocatore: casistica di fatto
            // impossibile con l'anagrafica fantacalcio.it (che disambigua sempre col suffisso), ma
            // gestita comunque come ambigua invece di scegliere a caso.
            log.warn("Match esatto ambiguo per nome normalizzato='{}': {} candidati", normalizedTarget, exact.size());
            return new ResolutionResult(null, exact);
        }

        List<PlayerCatalogDto> byContainment = sameRoleEntries.stream()
                .filter(entry -> matchesByContainment(normalizedTarget, normalize(entry.getNome())))
                .toList();
        if (byContainment.size() == 1) {
            return new ResolutionResult(byContainment.get(0), List.of());
        }
        return new ResolutionResult(null, byContainment);
    }

    private static boolean matchesByContainment(String normalizedTarget, String normalizedCandidate) {
        if (normalizedTarget.length() < MIN_SUBSTRING_LENGTH || normalizedCandidate.length() < MIN_SUBSTRING_LENGTH) {
            return false;
        }
        return normalizedCandidate.contains(normalizedTarget) || normalizedTarget.contains(normalizedCandidate);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String noAccents = Normalizer.normalize(value.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase().replaceAll("\\s+", " ");
    }
}
