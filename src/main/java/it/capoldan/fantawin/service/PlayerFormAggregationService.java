package it.capoldan.fantawin.service;

import it.capoldan.fantawin.dto.PlayerMatchStatDto;
import it.capoldan.fantawin.middleware.dao.dynamo.PlayerMatchStatDao;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

@Service
public class PlayerFormAggregationService {

    private static final int RECENT_MATCHES_WINDOW = 5;

    public record FormComponents(double recentFormScore, double historicalVsOpponentScore, double xgXaScore) {}

    private final PlayerMatchStatDao playerMatchStatDao;

    public PlayerFormAggregationService(PlayerMatchStatDao playerMatchStatDao) {
        this.playerMatchStatDao = playerMatchStatDao;
    }

    public Mono<FormComponents> computeFormComponents(String playerId, String opponentRealTeam) {
        return playerMatchStatDao.findByPlayer(playerId)
                .collectList()
                .map(history -> {
                    double recentForm = averageFantavoto(recentMatches(history));
                    double historical = historicalVsOpponent(history, opponentRealTeam, recentForm);
                    double xgXa = xgXaComponent(recentMatches(history));
                    return new FormComponents(recentForm, historical, xgXa);
                });
    }

    private List<PlayerMatchStatDto> recentMatches(List<PlayerMatchStatDto> history) {
        return history.stream()
                .sorted(Comparator.comparing(PlayerMatchStatDto::getMatchDay).reversed())
                .limit(RECENT_MATCHES_WINDOW)
                .toList();
    }

    private double averageFantavoto(List<PlayerMatchStatDto> matches) {
        return matches.stream()
                .filter(m -> m.getFantavoto() != null)
                .mapToDouble(PlayerMatchStatDto::getFantavoto)
                .average()
                .orElse(0.0);
    }

    private double historicalVsOpponent(List<PlayerMatchStatDto> history, String opponentRealTeam, double fallback) {
        if (opponentRealTeam == null) return fallback;
        List<PlayerMatchStatDto> vsOpponent = history.stream()
                .filter(m -> opponentRealTeam.equalsIgnoreCase(m.getOpponentTeam()))
                .toList();
        // fallback sulla forma recente se non c'e' storico specifico contro l'avversario
        return vsOpponent.isEmpty() ? fallback : averageFantavoto(vsOpponent);
    }

    /**
     * ASSUNZIONE/EURISTICA da calibrare: xG/xA grezzi (tipicamente 0.0-1.0 a partita)
     * vengono scalati su base 0-10 sommando i due valori medi e applicando un fattore.
     * Il README chiede solo "premia xG/xA alti anche senza gol, penalizza contro difese chiuse":
     * qui do un'implementazione plausibile ma è un parametro da tarare con dati reali.
     */
    private double xgXaComponent(List<PlayerMatchStatDto> recent) {
        double avgXg = recent.stream().filter(m -> m.getXg() != null).mapToDouble(PlayerMatchStatDto::getXg).average().orElse(0.0);
        double avgXa = recent.stream().filter(m -> m.getXa() != null).mapToDouble(PlayerMatchStatDto::getXa).average().orElse(0.0);
        double scaleFactor = 10.0; // TODO: calibrare
        return Math.min(10.0, (avgXg + avgXa) * scaleFactor);
    }
}