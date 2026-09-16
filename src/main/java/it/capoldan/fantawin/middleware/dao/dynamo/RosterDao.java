package it.capoldan.fantawin.middleware.dao.dynamo;

import it.capoldan.fantawin.dto.RosterDto;
import it.capoldan.fantawin.middleware.dao.dynamo.entity.RosterEntity;
import it.capoldan.fantawin.middleware.dao.dynamo.mapper.RosterEntityMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;

@Slf4j
@Repository
public class RosterDao implements BaseDao<RosterDto> {

    private final DynamoDbAsyncTable<RosterEntity> table;

    public RosterDao(DynamoDbAsyncTable<RosterEntity> rosterTable) {
        this.table = rosterTable;
    }

    public Mono<RosterDto> getById(String rosterId) {
        log.info("Recupero Roster per rosterId={}", rosterId);
        Key key = Key.builder().partitionValue(rosterId).build();
        return Mono.fromFuture(table.getItem(key))
                .map(RosterEntityMapper::toDto)
                .doOnError(ex -> log.warn("Errore nel recupero di Roster per rosterId={}", rosterId, ex));
    }

    @Override
    public Mono<RosterDto> save(RosterDto dto) {
        int numPlayers = dto.getPlayers() == null ? 0 : dto.getPlayers().size();
        log.info("Salvo Roster rosterId={} teamName={} numGiocatori={}", dto.getRosterId(), dto.getTeamName(), numPlayers);
        RosterEntity entity = RosterEntityMapper.toEntity(dto);
        return Mono.fromFuture(table.putItem(r -> r.item(entity)))
                .thenReturn(RosterEntityMapper.toDto(entity))
                .doOnSuccess(saved -> log.info("Roster salvato rosterId={}", dto.getRosterId()))
                .doOnError(ex -> log.warn("Errore nel salvataggio di Roster rosterId={}", dto.getRosterId(), ex));
    }

    @Override
    public Mono<RosterDto> update(RosterDto dto) {
        log.info("Aggiorno Roster rosterId={}", dto.getRosterId());
        RosterEntity entity = RosterEntityMapper.toEntity(dto);
        UpdateItemEnhancedRequest<RosterEntity> request = UpdateItemEnhancedRequest
                .builder(RosterEntity.class)
                .item(entity)
                .ignoreNulls(true)
                .build();
        return Mono.fromFuture(table.updateItem(request))
                .map(RosterEntityMapper::toDto)
                .doOnSuccess(updated -> log.info("Roster aggiornato rosterId={}", dto.getRosterId()))
                .doOnError(ex -> log.warn("Errore nell'aggiornamento di Roster rosterId={}", dto.getRosterId(), ex));
    }

    public Mono<RosterDto> delete(String rosterId) {
        log.info("Elimino Roster rosterId={}", rosterId);
        Key key = Key.builder().partitionValue(rosterId).build();
        return Mono.fromFuture(table.deleteItem(key))
                .map(RosterEntityMapper::toDto)
                .doOnSuccess(deleted -> log.info("Roster eliminato rosterId={}", rosterId))
                .doOnError(ex -> log.warn("Errore nell'eliminazione di Roster rosterId={}", rosterId, ex));
    }
}
