package it.capoldan.fantawin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RosterDto {

    private String rosterId;

    private String teamName;

    private List<RosterPlayerDto> players;

    private String ownerId;
}