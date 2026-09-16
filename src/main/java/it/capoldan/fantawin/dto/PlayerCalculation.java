package it.capoldan.fantawin.dto;

import it.capoldan.fantawin.generated.openapi.server.v1.dto.PlayerRatingDetails;

/** Coppia DTO-API + voto puro grezzo per il Modificatore di Difesa,
 *  cosi' il FormationSelector non deve mai tornare a interrogare Dynamo. */
public record PlayerCalculation(PlayerRatingDetails details, double recentPureVoteAverage) {}