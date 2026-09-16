package it.capoldan.fantawin.middleware.dao.dynamo;

import it.capoldan.fantawin.dto.AvailabilityReportDto;
import it.capoldan.fantawin.middleware.dao.dynamo.entity.AvailabilityReportEntity;
import it.capoldan.fantawin.middleware.dao.dynamo.mapper.AvailabilityReportEntityMapper;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;

@Repository
public class AvailabilityReportDao implements BaseDao<AvailabilityReportDto> {

    private final DynamoDbAsyncTable<AvailabilityReportEntity> table;

    public AvailabilityReportDao(DynamoDbAsyncTable<AvailabilityReportEntity> availabilityReportTable) {
        this.table = availabilityReportTable;
    }

    public Mono<AvailabilityReportDto> getById(String playerId) {
        Key key = Key.builder().partitionValue(playerId).build();
        return Mono.fromFuture(table.getItem(key)).map(AvailabilityReportEntityMapper::toDto);
    }

    public Flux<AvailabilityReportDto> findAll() {
        return Flux.from(table.scan().items()).map(AvailabilityReportEntityMapper::toDto);
    }

    @Override
    public Mono<AvailabilityReportDto> save(AvailabilityReportDto dto) {
        AvailabilityReportEntity entity = AvailabilityReportEntityMapper.toEntity(dto);
        return Mono.fromFuture(table.putItem(r -> r.item(entity)))
                .thenReturn(AvailabilityReportEntityMapper.toDto(entity));
    }

    @Override
    public Mono<AvailabilityReportDto> update(AvailabilityReportDto dto) {
        AvailabilityReportEntity entity = AvailabilityReportEntityMapper.toEntity(dto);
        UpdateItemEnhancedRequest<AvailabilityReportEntity> request = UpdateItemEnhancedRequest
                .builder(AvailabilityReportEntity.class)
                .item(entity)
                .ignoreNulls(true)
                .build();
        return Mono.fromFuture(table.updateItem(request)).map(AvailabilityReportEntityMapper::toDto);
    }

    public Mono<AvailabilityReportDto> delete(String playerId) {
        Key key = Key.builder().partitionValue(playerId).build();
        return Mono.fromFuture(table.deleteItem(key)).map(AvailabilityReportEntityMapper::toDto);
    }
}