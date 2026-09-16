package it.capoldan.fantawin.middleware.dao.dynamo;

import it.capoldan.fantawin.dto.RosterEntryDto;
import it.capoldan.fantawin.middleware.dao.dynamo.entity.RosterEntryEntity;
import it.capoldan.fantawin.middleware.dao.dynamo.mapper.RosterEntryEntityMapper;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;

@Repository
public class RosterEntryDao implements BaseDao<RosterEntryDto> {

    private final DynamoDbAsyncTable<RosterEntryEntity> table;

    public RosterEntryDao(DynamoDbAsyncTable<RosterEntryEntity> rosterEntryTable) {
        this.table = rosterEntryTable;
    }

    public Mono<RosterEntryDto> getByRosterAndPlayer(String rosterId, String playerId) {
        Key key = Key.builder().partitionValue(rosterId).sortValue(playerId).build();
        return Mono.fromFuture(table.getItem(key)).map(RosterEntryEntityMapper::toDto);
    }

    /** Rosa completa (usata da GET /roster) */
    public Flux<RosterEntryDto> findByRoster(String rosterId) {
        QueryConditional condition = QueryConditional.keyEqualTo(k -> k.partitionValue(rosterId));
        return Flux.from(table.query(condition).items()).map(RosterEntryEntityMapper::toDto);
    }

    @Override
    public Mono<RosterEntryDto> save(RosterEntryDto dto) {
        RosterEntryEntity entity = RosterEntryEntityMapper.toEntity(dto);
        return Mono.fromFuture(table.putItem(r -> r.item(entity)))
                .thenReturn(RosterEntryEntityMapper.toDto(entity));
    }

    @Override
    public Mono<RosterEntryDto> update(RosterEntryDto dto) {
        RosterEntryEntity entity = RosterEntryEntityMapper.toEntity(dto);
        UpdateItemEnhancedRequest<RosterEntryEntity> request = UpdateItemEnhancedRequest
                .builder(RosterEntryEntity.class)
                .item(entity)
                .ignoreNulls(true)
                .build();
        return Mono.fromFuture(table.updateItem(request)).map(RosterEntryEntityMapper::toDto);
    }

    public Mono<RosterEntryDto> delete(String rosterId, String playerId) {
        Key key = Key.builder().partitionValue(rosterId).sortValue(playerId).build();
        return Mono.fromFuture(table.deleteItem(key)).map(RosterEntryEntityMapper::toDto);
    }
}