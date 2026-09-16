package it.capoldan.fantawin.middleware.dao.dynamo;

import it.capoldan.fantawin.dto.FixtureDto;
import it.capoldan.fantawin.middleware.dao.dynamo.entity.FixtureEntity;
import it.capoldan.fantawin.middleware.dao.dynamo.mapper.FixtureEntityMapper;
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
public class FixtureDao implements BaseDao<FixtureDto> {

    private final DynamoDbAsyncTable<FixtureEntity> table;

    public FixtureDao(DynamoDbAsyncTable<FixtureEntity> fixtureTable) {
        this.table = fixtureTable;
    }

    public Mono<FixtureDto> getByMatchdayAndTeam(Integer matchday, String realTeam) {
        log.info("Recupero Fixture per matchday={} realTeam={}", matchday, realTeam);
        Key key = Key.builder().partitionValue(matchday).sortValue(realTeam).build();
        return Mono.fromFuture(table.getItem(key))
                .map(FixtureEntityMapper::toDto)
                .doOnError(ex -> log.warn("Errore nel recupero di Fixture per matchday={} realTeam={}", matchday, realTeam, ex));
    }

    /** Tutte le fixture di una giornata (query sulla sola PK) */
    public Flux<FixtureDto> findByMatchday(Integer matchday) {
        log.info("Query Fixtures per matchday={}", matchday);
        QueryConditional condition = QueryConditional.keyEqualTo(k -> k.partitionValue(matchday));
        return Flux.from(table.query(condition).items())
                .map(FixtureEntityMapper::toDto)
                .doOnError(ex -> log.warn("Errore nella query di Fixtures per matchday={}", matchday, ex));
    }

    @Override
    public Mono<FixtureDto> save(FixtureDto dto) {
        log.info("Salvo Fixture per matchday={} realTeam={} opponentTeam={}", dto.getMatchDay(), dto.getRealTeam(), dto.getOpponentTeam());
        FixtureEntity entity = FixtureEntityMapper.toEntity(dto);
        return Mono.fromFuture(table.putItem(r -> r.item(entity)))
                .thenReturn(FixtureEntityMapper.toDto(entity))
                .doOnSuccess(saved -> log.info("Fixture salvata per matchday={} realTeam={}", dto.getMatchDay(), dto.getRealTeam()))
                .doOnError(ex -> log.warn("Errore nel salvataggio di Fixture per matchday={} realTeam={}", dto.getMatchDay(), dto.getRealTeam(), ex));
    }

    @Override
    public Mono<FixtureDto> update(FixtureDto dto) {
        log.info("Aggiorno Fixture per matchday={} realTeam={}", dto.getMatchDay(), dto.getRealTeam());
        FixtureEntity entity = FixtureEntityMapper.toEntity(dto);
        UpdateItemEnhancedRequest<FixtureEntity> request = UpdateItemEnhancedRequest
                .builder(FixtureEntity.class)
                .item(entity)
                .ignoreNulls(true)
                .build();
        return Mono.fromFuture(table.updateItem(request))
                .map(FixtureEntityMapper::toDto)
                .doOnSuccess(updated -> log.info("Fixture aggiornata per matchday={} realTeam={}", dto.getMatchDay(), dto.getRealTeam()))
                .doOnError(ex -> log.warn("Errore nell'aggiornamento di Fixture per matchday={} realTeam={}", dto.getMatchDay(), dto.getRealTeam(), ex));
    }

    public Mono<FixtureDto> delete(Integer matchday, String realTeam) {
        log.info("Elimino Fixture per matchday={} realTeam={}", matchday, realTeam);
        Key key = Key.builder().partitionValue(matchday).sortValue(realTeam).build();
        return Mono.fromFuture(table.deleteItem(key))
                .map(FixtureEntityMapper::toDto)
                .doOnSuccess(deleted -> log.info("Fixture eliminata per matchday={} realTeam={}", matchday, realTeam))
                .doOnError(ex -> log.warn("Errore nell'eliminazione di Fixture per matchday={} realTeam={}", matchday, realTeam, ex));
    }
}
