package it.capoldan.fantawin.utils;

import it.capoldan.fantawin.generated.openapi.server.v1.dto.FantaRatingBreakdown;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.LineupResponse;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.Player;
import it.capoldan.fantawin.generated.openapi.server.v1.dto.PlayerRatingDetails;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Stampa nei log la formazione restituita da POST /fanta-private/predict/lineup cosi' come si
 * vede in TV: modulo disegnato "ad albero" (attaccanti in alto, portiere in basso, come sulle
 * grafiche di Sky/DAZN), seguito dalle statistiche di dettaglio di titolari e panchina.
 * Solo un aiuto per la lettura nei log: non altera in alcun modo la response dell'API.
 */
@Slf4j
public class LineupFormationLogger {

    private LineupFormationLogger() {
    }

    public static void logFormation(LineupResponse response) {
        if (response == null || response.getStartingEleven() == null) {
            log.warn("Impossibile stampare la formazione: response o undici titolare assenti");
            return;
        }

        List<PlayerRatingDetails> eleven = response.getStartingEleven();
        int[] shape = parseFormation(response.getWinningFormation());
        if (shape == null || eleven.size() != 11) {
            log.warn("Formazione '{}' non nel formato atteso D-M-F oppure undici titolare incompleto ({} giocatori): salto il disegno del modulo",
                    response.getWinningFormation(), eleven.size());
            logStatistics(response);
            return;
        }

        List<PlayerRatingDetails> goalkeeper = eleven.subList(0, 1);
        List<PlayerRatingDetails> defenders = eleven.subList(1, 1 + shape[0]);
        List<PlayerRatingDetails> midfielders = eleven.subList(1 + shape[0], 1 + shape[0] + shape[1]);
        List<PlayerRatingDetails> forwards = eleven.subList(1 + shape[0] + shape[1], 11);

        int width = widestRow(forwards, midfielders, defenders, goalkeeper);

        StringBuilder tree = new StringBuilder();
        tree.append("\n=== FORMAZIONE ").append(response.getWinningFormation())
                .append(" - punteggio totale ").append(response.getTotalExpectedScore())
                .append(" (bonus difesa +").append(response.getDefenseModifierBonus()).append(") ===\n\n");
        appendRow(tree, forwards, width);
        appendRow(tree, midfielders, width);
        appendRow(tree, defenders, width);
        appendRow(tree, goalkeeper, width);

        log.info(tree.toString());
        logStatistics(response);
    }

    private static void logStatistics(LineupResponse response) {
        StringBuilder stats = new StringBuilder("\n=== STATISTICHE TITOLARI ===\n");
        response.getStartingEleven().forEach(p -> stats.append(formatPlayerStat(p)).append('\n'));

        stats.append("\n=== STATISTICHE PANCHINA ===\n");
        if (response.getBench() != null && !response.getBench().isEmpty()) {
            response.getBench().forEach(p -> stats.append(formatPlayerStat(p)).append('\n'));
        } else {
            stats.append("(panchina vuota)\n");
        }

        log.info(stats.toString());
    }

    private static String formatPlayerStat(PlayerRatingDetails p) {
        Player player = p.getPlayer();
        FantaRatingBreakdown b = p.getFantaRatingBreakdown();
        return String.format(
                "%-4s %-20s %-12s FantaRating=%5.2f  [forma=%.2f storico=%.2f match/xG=%.2f infrasett=%.2f disponibilita=%.2f]",
                player.getPosition(), player.getName(), player.getRealTeam(), p.getFantaRating(),
                b.getBaseFormScore(), b.getHistoricalScore(), b.getMatchDifficultyXgScore(),
                b.getMidweekPenalty(), b.getAvailabilityPenalty());
    }

    private static void appendRow(StringBuilder sb, List<PlayerRatingDetails> row, int width) {
        String names = row.stream().map(p -> p.getPlayer().getName()).collect(Collectors.joining("   "));
        int padding = Math.max(0, (width - names.length()) / 2);
        sb.append(" ".repeat(padding)).append(names).append('\n');
    }

    @SafeVarargs
    private static int widestRow(List<PlayerRatingDetails>... rows) {
        int max = 0;
        for (List<PlayerRatingDetails> row : rows) {
            String names = row.stream().map(p -> p.getPlayer().getName()).collect(Collectors.joining("   "));
            max = Math.max(max, names.length());
        }
        return max;
    }

    /** Converte "4-3-3" in [4,3,3] (DIF-CEN-ATT); null se il formato non e' quello atteso. */
    private static int[] parseFormation(String formation) {
        if (formation == null) return null;
        String[] parts = formation.split("-");
        if (parts.length != 3) return null;
        try {
            return new int[]{
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
