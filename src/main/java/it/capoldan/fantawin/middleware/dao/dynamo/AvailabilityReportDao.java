package it.capoldan.fantawin.middleware.dao.dynamo;

import it.capoldan.fantawin.dto.AvailabilityReportDto;
import it.capoldan.fantawin.middleware.dao.dynamo.entity.AvailabilityReportEntity;
import it.capoldan.fantawin.middleware.dao.dynamo.mapper.AvailabilityReportEntityMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;

@Slf4j
@Repository
public class AvailabilityReportDao implements BaseDao<AvailabilityReportDto> {

    private final DynamoDbAsyncTable<AvailabilityReportEntity> table;

    public AvailabilityReportDao(DynamoDbAsyncTable<AvailabilityReportEntity> availabilityReportTable) {
        this.table = availabilityReportTable;
    }

    public Mono<AvailabilityReportDto> getById(String playerId) {
        log.info("Recupero AvailabilityReport per playerId={}", playerId);
        Key key = Key.builder().partitionValue(playerId).build();
        return Mono.fromFuture(table.getItem(key))
                .map(AvailabilityReportEntityMapper::toDto)
                .doOnError(ex -> log.warn("Errore nel recupero di AvailabilityReport per playerId={}", playerId, ex));
    }

    public Flux<AvailabilityReportDto> findAll() {
        log.info("Scan completo della tabella AvailabilityReports");
        return Flux.from(table.scan().items())
                .map(AvailabilityReportEntityMapper::toDto)
                .doOnError(ex -> log.warn("Errore durante lo scan della tabella AvailabilityReports", ex));
    }

    @Override
    public Mono<AvailabilityReportDto> save(AvailabilityReportDto dto) {
        log.info("Salvo AvailabilityReport per playerId={} status={}", dto.getPlayerId(), dto.getStatus());
        AvailabilityReportEntity entity = AvailabilityReportEntityMapper.toEntity(dto);
        return Mono.fromFuture(table.putItem(r -> r.item(entity)))
                .thenReturn(AvailabilityReportEntityMapper.toDto(entity))
                .doOnSuccess(saved -> log.info("AvailabilityReport salvato per playerId={}", dto.getPlayerId()))
                .doOnError(ex -> log.warn("Errore nel salvataggio di AvailabilityReport per playerId={}", dto.getPlayerId(), ex));
    }

    @Override
    public Mono<AvailabilityReportDto> update(AvailabilityReportDto dto) {
        log.info("Aggiorno AvailabilityReport per playerId={}", dto.getPlayerId());
        AvailabilityReportEntity entity = AvailabilityReportEntityMapper.toEntity(dto);
        UpdateItemEnhancedRequest<AvailabilityReportEntity> request = UpdateItemEnhancedRequest
                .builder(AvailabilityReportEntity.class)
                .item(entity)
                .ignoreNulls(true)
                .build();
        return Mono.fromFuture(table.updateItem(request))
                .map(AvailabilityReportEntityMapper::toDto)
                .doOnSuccess(updated -> log.info("AvailabilityReport aggiornato per playerId={}", dto.getPlayerId()))
                .doOnError(ex -> log.warn("Errore nell'aggiornamento di AvailabilityReport per playerId={}", dto.getPlayerId(), ex));
    }

    public Mono<AvailabilityReportDto> delete(String playerId) {
        log.info("Elimino AvailabilityReport per playerId={}", playerId);
        Key key = Key.builder().partitionValue(playerId).build();
        return Mono.fromFuture(table.deleteItem(key))
                .map(AvailabilityReportEntityMapper::toDto)
                .doOnSuccess(deleted -> log.info("AvailabilityReport eliminato per playerId={}", playerId))
                .doOnError(ex -> log.warn("Errore nell'eliminazione di AvailabilityReport per playerId={}", playerId, ex));
    }
}
