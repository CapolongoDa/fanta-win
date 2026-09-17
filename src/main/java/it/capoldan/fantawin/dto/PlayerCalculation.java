package it.capoldan.fantawin.dto;

import it.capoldan.fantawin.generated.openapi.server.v1.dto.PlayerRatingDetails;

/** DTO-API + voto puro e fantavoto medio grezzi (ultime 5 giornate), cosi' il FormationSelector non
 *  deve mai tornare a interrogare Dynamo per calcolare il Modificatore di Difesa: recentPureVoteAverage
 *  non e' piu' usato nella griglia (che ora si basa sul fantavoto, comprensivo dei bonus gol dei
 *  difensori/portiere), ma resta disponibile per eventuali altri usi del voto puro. */
public record PlayerCalculation(PlayerRatingDetails details, double recentPureVoteAverage, double recentFantavotoAverage) {}