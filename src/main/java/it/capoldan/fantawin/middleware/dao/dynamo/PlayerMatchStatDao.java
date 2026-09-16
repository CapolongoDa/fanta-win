package it.capoldan.fantawin.middleware.dao.dynamo;

import it.capoldan.fantawin.dto.PlayerMatchStatDto;
import it.capoldan.fantawin.middleware.dao.dynamo.entity.PlayerMatchStatEntity;
import it.capoldan.fantawin.middleware.dao.dynamo.mapper.PlayerMatchStatEntityMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;

@Slf4j
@Repository
public class PlayerMatchStatDao implements BaseDao<PlayerMatchStatDto> {

    private final DynamoDbAsyncTable<PlayerMatchStatEntity> table;

    public PlayerMatchStatDao(DynamoDbAsyncTable<PlayerMatchStatEntity> playerMatchStatTable) {
        this.table = playerMatchStatTable;
    }

    public Mono<PlayerMatchStatDto> getByPlayerAndMatchday(String playerId, Integer matchday) {
        log.info("Recupero PlayerMatchStat per playerId={} matchday={}", playerId, matchday);
        Key key = Key.builder().partitionValue(playerId).sortValue(matchday).build();
        return Mono.fromFuture(table.getItem(key))
                .map(PlayerMatchStatEntityMapper::toDto)
                .doOnError(ex -> log.warn("Errore nel recupero di PlayerMatchStat per playerId={} matchday={}", playerId, matchday, ex));
    }

    /** Storico completo di un giocatore, utile per il 40% "forma recente" del FantaRating */
    public Flux<PlayerMatchStatDto> findByPlayer(String playerId) {
        log.info("Query storico PlayerMatchStats per playerId={}", playerId);
        QueryConditional condition = QueryConditional.keyEqualTo(k -> k.partitionValue(playerId));
        return Flux.from(table.query(condition).items())
                .map(PlayerMatchStatEntityMapper::toDto)
                .doOnError(ex -> log.warn("Errore nella query dello storico PlayerMatchStats per playerId={}", playerId, ex));
    }

    @Override
    public Mono<PlayerMatchStatDto> save(PlayerMatchStatDto dto) {
        log.info("Salvo PlayerMatchStat playerId={} matchday={} voto={} fantavoto={}",
                dto.getPlayerId(), dto.getMatchDay(), dto.getVoto(), dto.getFantavoto());
        PlayerMatchStatEntity entity = PlayerMatchStatEntityMapper.toEntity(dto);
        return Mono.fromFuture(table.putItem(r -> r.item(entity)))
                .thenReturn(PlayerMatchStatEntityMapper.toDto(entity))
                .doOnSuccess(saved -> log.info("PlayerMatchStat salvato playerId={} matchday={}", dto.getPlayerId(), dto.getMatchDay()))
                .doOnError(ex -> log.warn("Errore nel salvataggio di PlayerMatchStat playerId={} matchday={}", dto.getPlayerId(), dto.getMatchDay(), ex));
    }

    @Override
    public Mono<PlayerMatchStatDto> update(PlayerMatchStatDto dto) {
        log.info("Aggiorno PlayerMatchStat playerId={} matchday={}", dto.getPlayerId(), dto.getMatchDay());
        PlayerMatchStatEntity entity = PlayerMatchStatEntityMapper.toEntity(dto);
        UpdateItemEnhancedRequest<PlayerMatchStatEntity> request = UpdateItemEnhancedRequest
                .builder(PlayerMatchStatEntity.class)
                .item(entity)
                .ignoreNulls(true)
                .build();
        return Mono.fromFuture(table.updateItem(request))
                .map(PlayerMatchStatEntityMapper::toDto)
                .doOnSuccess(updated -> log.info("PlayerMatchStat aggiornato playerId={} matchday={}", dto.getPlayerId(), dto.getMatchDay()))
                .doOnError(ex -> log.warn("Errore nell'aggiornamento di PlayerMatchStat playerId={} matchday={}", dto.getPlayerId(), dto.getMatchDay(), ex));
    }

    public Mono<PlayerMatchStatDto> delete(String playerId, Integer matchday) {
        log.info("Elimino PlayerMatchStat playerId={} matchday={}", playerId, matchday);
        Key key = Key.builder().partitionValue(playerId).sortValue(matchday).build();
        return Mono.fromFuture(table.deleteItem(key))
                .map(PlayerMatchStatEntityMapper::toDto)
                .doOnSuccess(deleted -> log.info("PlayerMatchStat eliminato playerId={} matchday={}", playerId, matchday))
                .doOnError(ex -> log.warn("Errore nell'eliminazione di PlayerMatchStat playerId={} matchday={}", playerId, matchday, ex));
    }
}
