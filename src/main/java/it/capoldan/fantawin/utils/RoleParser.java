package it.capoldan.fantawin.utils;

import it.capoldan.fantawin.dto.Role;

import java.util.Map;

/**
 * Interpreta la stringa "ruolo" fornita dall'utente (bulk-add giocatori, import CSV del catalogo)
 * accettando la sigla classica fantacalcio.it (P/D/C/A, case-insensitive), la spelling completa
 * usata internamente (POR/DIF/CEN/ATT) e alcune sigle "Mantra" comuni che i clienti si aspettano
 * di poter usare intercambiabilmente con quelle classiche (es. CC per centrocampista centrale, DC
 * per difensore centrale). Non tenta correzioni automatiche su valori non riconosciuti: ritorna
 * null e lascia al chiamante decidere come segnalarlo (mai indovinare un ruolo).
 */
public final class RoleParser {

    /** Alias accettati oltre alla spelling completa (gestita da Role.valueOf): sia le sigle
     * classiche a singola lettera, sia alcune sigle "Mantra" a due lettere che compaiono spesso
     * nei dati incollati dagli utenti pur non essendo Role enum values. */
    private static final Map<String, Role> ALIASES = Map.of(
            "P", Role.POR,
            "D", Role.DIF,
            "C", Role.CEN,
            "A", Role.ATT,
            "DC", Role.DIF,
            "CC", Role.CEN
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
        Role byAlias = ALIASES.get(v);
        if (byAlias != null) {
            return byAlias;
        }
        try {
            return Role.valueOf(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
