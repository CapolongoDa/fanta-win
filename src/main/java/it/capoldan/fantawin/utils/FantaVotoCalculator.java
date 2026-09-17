package it.capoldan.fantawin.utils;

/**
 * Calcola il fantavoto a partire dal voto base e dai bonus/malus di giornata, invece di fidarsi di
 * un valore "fantavoto" inserito a mano nel CSV di import (facile da sbagliare/disallineare rispetto
 * a gol/assist/cartellini): FantaWin lo ricalcola sempre da qui, unica fonte di verita' per la formula.
 *
 * Regole (fornite dall'utente):
 * - gol fatto: +3
 * - assist: +1
 * - gol subito: -1 (pensato per portiere/difensori: per centrocampisti/attaccanti lascia il campo a 0)
 * - ammonizione: -0.5
 * - espulsione: -1
 */
public class FantaVotoCalculator {

    private static final double PUNTI_GOL_FATTO = 3.0;
    private static final double PUNTI_ASSIST = 1.0;
    private static final double MALUS_GOL_SUBITO = 1.0;
    private static final double MALUS_AMMONIZIONE = 0.5;
    private static final double MALUS_ESPULSIONE = 1.0;

    private FantaVotoCalculator() {}

    /** Restituisce null se manca il voto base (giocatore non in campo/Senza Voto): senza voto non c'e' fantavoto. */
    public static Double calcola(Double voto, Integer gol, Integer assist, Integer golSubiti, Integer ammonizioni,
                                 Integer espulsioni) {
        if (voto == null) {
            return null;
        }
        return voto
                + orZero(gol) * PUNTI_GOL_FATTO
                + orZero(assist) * PUNTI_ASSIST
                - orZero(golSubiti) * MALUS_GOL_SUBITO
                - orZero(ammonizioni) * MALUS_AMMONIZIONE
                - orZero(espulsioni) * MALUS_ESPULSIONE;
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
