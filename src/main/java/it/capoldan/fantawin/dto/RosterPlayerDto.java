package it.capoldan.fantawin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RosterPlayerDto {

    private String playerId;

    private Role fantasyRole;
}