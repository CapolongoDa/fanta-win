package it.capoldan.fantawin.utils;

import it.capoldan.fantawin.dto.Role;

import java.util.Map;

/**
 * Interpreta la stringa "ruolo" fornita dall'utente (bulk-add giocatori, import CSV del catalogo)
 * accettando sia la sigla classica fantacalcio.it (P/D/C/A, case-insensitive) sia la spelling
 * completa usata internamente (POR/DIF/CEN/ATT). Non tenta correzioni automatiche su valori non
 * riconosciuti: ritorna null e lascia al chiamante decidere come segnalarlo (mai indovinare un ruolo).
 */
public final class RoleParser {

    private static final Map<String, Role> LETTER_ALIASES = Map.of(
            "P", Role.POR,
            "D", Role.DIF,
            "C", Role.CEN,
            "A", Role.ATT
    );

    private RoleParser() {
    }

    public static Role parse(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim().toUpperCase();
        if (v.isEmpty()) {
            return null;
        }
        Role byLetter = LETTER_ALIASES.get(v);
        if (byLetter != null) {
            return byLetter;
        }
        try {
            return Role.valueOf(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
