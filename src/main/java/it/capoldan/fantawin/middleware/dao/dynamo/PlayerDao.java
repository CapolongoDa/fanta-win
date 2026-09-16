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
        log.info("Recupero Player per playerId={}", playerId);
        Key key = Key.builder().partitionValue(playerId).build();
        return Mono.fromFuture(table.getItem(key))
                .map(PlayerEntityMapper::toDto)
                .doOnError(ex -> log.warn("Errore nel recupero di Player per playerId={}", playerId, ex));
    }

    public Flux<PlayerDto> findAll() {
        log.info("Scan completo della tabella Players");
        return Flux.from(table.scan().items())
                .map(PlayerEntityMapper::toDto)
                .doOnError(ex -> log.warn("Errore durante lo scan della tabella Players", ex));
    }

    /** Crea o sovrascrive completamente l'item */
    @Override
    public Mono<PlayerDto> save(PlayerDto dto) {
        log.info("Salvo Player playerId={} name={} realTeam={}", dto.getPlayerId(), dto.getName(), dto.getRealTeam());
        PlayerEntity entity = PlayerEntityMapper.toEntity(dto);
        return Mono.fromFuture(table.putItem(r -> r.item(entity)))
                .thenReturn(PlayerEntityMapper.toDto(entity))
                .doOnSuccess(saved -> log.info("Player salvato playerId={}", dto.getPlayerId()))
                .doOnError(ex -> log.warn("Errore nel salvataggio di Player playerId={}", dto.getPlayerId(), ex));
    }

    /** Aggiornamento parziale: i campi null nel dto non sovrascrivono i valori esistenti */
    @Override
    public Mono<PlayerDto> update(PlayerDto dto) {
        log.info("Aggiorno Player playerId={}", dto.getPlayerId());
        PlayerEntity entity = PlayerEntityMapper.toEntity(dto);
        UpdateItemEnhancedRequest<PlayerEntity> request = UpdateItemEnhancedRequest
                .builder(PlayerEntity.class)
                .item(entity)
                .ignoreNulls(true)
                .build();
        return Mono.fromFuture(table.updateItem(request))
                .map(PlayerEntityMapper::toDto)
                .doOnSuccess(updated -> log.info("Player aggiornato playerId={}", dto.getPlayerId()))
                .doOnError(ex -> log.warn("Errore nell'aggiornamento di Player playerId={}", dto.getPlayerId(), ex));
    }

    public Mono<PlayerDto> delete(String playerId) {
        log.info("Elimino Player playerId={}", playerId);
        Key key = Key.builder().partitionValue(playerId).build();
        return Mono.fromFuture(table.deleteItem(key))
                .map(PlayerEntityMapper::toDto)
                .doOnSuccess(deleted -> log.info("Player eliminato playerId={}", playerId))
                .doOnError(ex -> log.warn("Errore nell'eliminazione di Player playerId={}", playerId, ex));
    }
}
