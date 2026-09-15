package it.capoldan.fantawin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerMatchStatDto {

    private String playerId;

    private Integer matchday;

    private String season;

    private String opponentTeam;

    private boolean home;

    private Double voto;

    private Double fantavoto;

    private Integer gol;

    private Integer assist;

    private Integer ammonizioni;

    private Integer espulsioni;

    private Double xg;

    private Double xa;
}