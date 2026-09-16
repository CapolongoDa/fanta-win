package it.capoldan.fantawin.middleware.dao.dynamo;

import it.capoldan.fantawin.dto.PlayerMatchStatDto;
import it.capoldan.fantawin.middleware.dao.dynamo.entity.PlayerMatchStatEntity;
import it.capoldan.fantawin.middleware.dao.dynamo.mapper.PlayerMatchStatEntityMapper;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;

@Repository
public class PlayerMatchStatDao implements BaseDao<PlayerMatchStatDto> {

    private final DynamoDbAsyncTable<PlayerMatchStatEntity> table;

    public PlayerMatchStatDao(DynamoDbAsyncTable<PlayerMatchStatEntity> playerMatchStatTable) {
        this.table = playerMatchStatTable;
    }

    public Mono<PlayerMatchStatDto> getByPlayerAndMatchday(String playerId, Integer matchday) {
        Key key = Key.builder().partitionValue(playerId).sortValue(matchday).build();
        return Mono.fromFuture(table.getItem(key)).map(PlayerMatchStatEntityMapper::toDto);
    }

    /** Storico completo di un giocatore, utile per il 40% "forma recente" del FantaRating */
    public Flux<PlayerMatchStatDto> findByPlayer(String playerId) {
        QueryConditional condition = QueryConditional.keyEqualTo(k -> k.partitionValue(playerId));
        return Flux.from(table.query(condition).items()).map(PlayerMatchStatEntityMapper::toDto);
    }

    @Override
    public Mono<PlayerMatchStatDto> save(PlayerMatchStatDto dto) {
        PlayerMatchStatEntity entity = PlayerMatchStatEntityMapper.toEntity(dto);
        return Mono.fromFuture(table.putItem(r -> r.item(entity)))
                .thenReturn(PlayerMatchStatEntityMapper.toDto(entity));
    }

    @Override
    public Mono<PlayerMatchStatDto> update(PlayerMatchStatDto dto) {
        PlayerMatchStatEntity entity = PlayerMatchStatEntityMapper.toEntity(dto);
        UpdateItemEnhancedRequest<PlayerMatchStatEntity> request = UpdateItemEnhancedRequest
                .builder(PlayerMatchStatEntity.class)
                .item(entity)
                .ignoreNulls(true)
                .build();
        return Mono.fromFuture(table.updateItem(request)).map(PlayerMatchStatEntityMapper::toDto);
    }

    public Mono<PlayerMatchStatDto> delete(String playerId, Integer matchday) {
        Key key = Key.builder().partitionValue(playerId).sortValue(matchday).build();
        return Mono.fromFuture(table.deleteItem(key)).map(PlayerMatchStatEntityMapper::toDto);
    }
}