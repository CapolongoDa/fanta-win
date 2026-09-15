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
public class PlayerDto {

    private String playerId;

    private String name;

    private String realTeam;

    private Role role;

    private boolean active;

    private Long version;
}