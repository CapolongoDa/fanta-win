package it.capoldan.fantawin.middleware.dao.dynamo;

import it.capoldan.fantawin.dto.RosterDto;
import it.capoldan.fantawin.middleware.dao.dynamo.entity.RosterEntity;
import it.capoldan.fantawin.middleware.dao.dynamo.mapper.RosterEntityMapper;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;

@Repository
public class RosterDao implements BaseDao<RosterDto> {

    private final DynamoDbAsyncTable<RosterEntity> table;

    public RosterDao(DynamoDbAsyncTable<RosterEntity> rosterTable) {
        this.table = rosterTable;
    }

    public Mono<RosterDto> getById(String rosterId) {
        Key key = Key.builder().partitionValue(rosterId).build();
        return Mono.fromFuture(table.getItem(key)).map(RosterEntityMapper::toDto);
    }

    @Override
    public Mono<RosterDto> save(RosterDto dto) {
        RosterEntity entity = RosterEntityMapper.toEntity(dto);
        return Mono.fromFuture(table.putItem(r -> r.item(entity)))
                .thenReturn(RosterEntityMapper.toDto(entity));
    }

    @Override
    public Mono<RosterDto> update(RosterDto dto) {
        RosterEntity entity = RosterEntityMapper.toEntity(dto);
        UpdateItemEnhancedRequest<RosterEntity> request = UpdateItemEnhancedRequest
                .builder(RosterEntity.class)
                .item(entity)
                .ignoreNulls(true)
                .build();
        return Mono.fromFuture(table.updateItem(request)).map(RosterEntityMapper::toDto);
    }

    public Mono<RosterDto> delete(String rosterId) {
        Key key = Key.builder().partitionValue(rosterId).build();
        return Mono.fromFuture(table.deleteItem(key)).map(RosterEntityMapper::toDto);
    }
}