package it.capoldan.fantawin.service;

import it.capoldan.fantawin.dto.PlayerMatchStatDto;
import it.capoldan.fantawin.middleware.dao.dynamo.PlayerMatchStatDao;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@Service
public class PlayerFormAggregationService {

    private static final int RECENT_MATCHES_WINDOW = 5;

    /** recentMatches e' esposto qui (non ricalcolato altrove) per evitare interrogazioni
     * duplicate a PlayerMatchStatsTable: serve sia a Player.recentScores nell'output API
     * sia al calcolo del Modificatore di Difesa (voto puro), senza chiamate bloccanti extra. */
    public record FormComponents(double recentFormScore,
                                 double historicalVsOpponentScore,
                                 double xgXaScore,
                                 double recentPureVoteAverage,
                                 List<PlayerMatchStatDto> recentMatches) {}

    private final PlayerMatchStatDao playerMatchStatDao;

    public PlayerFormAggregationService(PlayerMatchStatDao playerMatchStatDao) {
        this.playerMatchStatDao = playerMatchStatDao;
    }

    public Mono<FormComponents> computeFormComponents(String playerId, String opponentRealTeam) {
        return playerMatchStatDao.findByPlayer(playerId)
                .collectList()
                .map(history -> {
                    List<PlayerMatchStatDto> recent = recentMatches(history);
                    double recentForm = average(recent, PlayerMatchStatDto::getFantavoto);
                    double historical = historicalVsOpponent(history, opponentRealTeam, recentForm);
                    double xgXa = xgXaComponent(recent);
                    double pureVote = average(recent, PlayerMatchStatDto::getVoto);
                    return new FormComponents(recentForm, historical, xgXa, pureVote, recent);
                });
    }

    private List<PlayerMatchStatDto> recentMatches(List<PlayerMatchStatDto> history) {
        return history.stream()
                .sorted(Comparator.comparing(PlayerMatchStatDto::getMatchDay).reversed())
                .limit(RECENT_MATCHES_WINDOW)
                .toList();
    }

    private double average(List<PlayerMatchStatDto> matches, Function<PlayerMatchStatDto, Double> extractor) {
        return matches.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private double historicalVsOpponent(List<PlayerMatchStatDto> history, String opponentRealTeam, double fallback) {
        if (opponentRealTeam == null) return fallback;
        List<PlayerMatchStatDto> vsOpponent = history.stream()
                .filter(m -> opponentRealTeam.equalsIgnoreCase(m.getOpponentTeam()))
                .toList();
        return vsOpponent.isEmpty() ? fallback : average(vsOpponent, PlayerMatchStatDto::getFantavoto);
    }

    /** ASSUNZIONE/EURISTICA da calibrare: fattore di scala arbitrario su xG+xA medi. */
    private double xgXaComponent(List<PlayerMatchStatDto> recent) {
        double avgXg = average(recent, PlayerMatchStatDto::getXg);
        double avgXa = average(recent, PlayerMatchStatDto::getXa);
        double scaleFactor = 10.0; // TODO: calibrare
        return Math.min(10.0, (avgXg + avgXa) * scaleFactor);
    }
}