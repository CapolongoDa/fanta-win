package it.capoldan.fantawin.middleware.dao.dynamo;

import it.capoldan.fantawin.dto.FixtureDto;
import it.capoldan.fantawin.middleware.dao.dynamo.entity.FixtureEntity;
import it.capoldan.fantawin.middleware.dao.dynamo.mapper.FixtureEntityMapper;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;

@Repository
public class FixtureDao implements BaseDao<FixtureDto> {

    private final DynamoDbAsyncTable<FixtureEntity> table;

    public FixtureDao(DynamoDbAsyncTable<FixtureEntity> fixtureTable) {
        this.table = fixtureTable;
    }

    public Mono<FixtureDto> getByMatchdayAndTeam(Integer matchday, String realTeam) {
        Key key = Key.builder().partitionValue(matchday).sortValue(realTeam).build();
        return Mono.fromFuture(table.getItem(key)).map(FixtureEntityMapper::toDto);
    }

    /** Tutte le fixture di una giornata (query sulla sola PK) */
    public Flux<FixtureDto> findByMatchday(Integer matchday) {
        QueryConditional condition = QueryConditional.keyEqualTo(k -> k.partitionValue(matchday));
        return Flux.from(table.query(condition).items()).map(FixtureEntityMapper::toDto);
    }

    @Override
    public Mono<FixtureDto> save(FixtureDto dto) {
        FixtureEntity entity = FixtureEntityMapper.toEntity(dto);
        return Mono.fromFuture(table.putItem(r -> r.item(entity)))
                .thenReturn(FixtureEntityMapper.toDto(entity));
    }

    @Override
    public Mono<FixtureDto> update(FixtureDto dto) {
        FixtureEntity entity = FixtureEntityMapper.toEntity(dto);
        UpdateItemEnhancedRequest<FixtureEntity> request = UpdateItemEnhancedRequest
                .builder(FixtureEntity.class)
                .item(entity)
                .ignoreNulls(true)
                .build();
        return Mono.fromFuture(table.updateItem(request)).map(FixtureEntityMapper::toDto);
    }

    public Mono<FixtureDto> delete(Integer matchday, String realTeam) {
        Key key = Key.builder().partitionValue(matchday).sortValue(realTeam).build();
        return Mono.fromFuture(table.deleteItem(key)).map(FixtureEntityMapper::toDto);
    }
}