package it.capoldan.fantawin.middleware.dao.dynamo;

import it.capoldan.fantawin.dto.PlayerDto;
import it.capoldan.fantawin.middleware.dao.dynamo.entity.PlayerEntity;
import it.capoldan.fantawin.middleware.dao.dynamo.mapper.PlayerEntityMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;

@Slf4j
@Repository
public class PlayerDao implements BaseDao<PlayerDto> {

    private final DynamoDbAsyncTable<PlayerEntity> table;

    public PlayerDao(DynamoDbAsyncTable<PlayerEntity> playerTable) {
        this.table = playerTable;
    }

    public Mono<PlayerDto> getById(String playerId) {
        Key key = Key.builder().partitionValue(playerId).build();
        return Mono.fromFuture(table.getItem(key)).map(PlayerEntityMapper::toDto);
    }

    public Flux<PlayerDto> findAll() {
        return Flux.from(table.scan().items()).map(PlayerEntityMapper::toDto);
    }

    /** Crea o sovrascrive completamente l'item */
    @Override
    public Mono<PlayerDto> save(PlayerDto dto) {
        PlayerEntity entity = PlayerEntityMapper.toEntity(dto);
        return Mono.fromFuture(table.putItem(r -> r.item(entity)))
                .thenReturn(PlayerEntityMapper.toDto(entity));
    }

    /** Aggiornamento parziale: i campi null nel dto non sovrascrivono i valori esistenti */
    @Override
    public Mono<PlayerDto> update(PlayerDto dto) {
        PlayerEntity entity = PlayerEntityMapper.toEntity(dto);
        UpdateItemEnhancedRequest<PlayerEntity> request = UpdateItemEnhancedRequest
                .builder(PlayerEntity.class)
                .item(entity)
                .ignoreNulls(true)
                .build();
        return Mono.fromFuture(table.updateItem(request)).map(PlayerEntityMapper::toDto);
    }

    public Mono<PlayerDto> delete(String playerId) {
        Key key = Key.builder().partitionValue(playerId).build();
        return Mono.fromFuture(table.deleteItem(key)).map(PlayerEntityMapper::toDto);
    }
}