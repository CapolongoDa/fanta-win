package it.capoldan.fantawin.dto;

import it.capoldan.fantawin.middleware.dao.dynamo.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RosterEntryDto {

    private String rosterId;

    private String playerId;

    private Role fantasyRole;
}