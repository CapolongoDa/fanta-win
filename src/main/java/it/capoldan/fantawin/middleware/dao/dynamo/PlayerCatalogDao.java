package it.capoldan.fantawin.middleware.dao.dynamo;

import it.capoldan.fantawin.dto.PlayerCatalogDto;
import it.capoldan.fantawin.middleware.dao.dynamo.entity.PlayerCatalogEntity;
import it.capoldan.fantawin.middleware.dao.dynamo.mapper.PlayerCatalogEntityMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

/**
 * Anagrafica completa di Serie A (tabella PlayerCatalog). Solo getById/findAll/save: non e' prevista
 * una delete/update parziale, viene semplicemente ri-scritta ad ogni import CSV (put-item, key = id
 * originale delle quotazioni fantacalcio.it).
 */
@Slf4j
@Repository
public class PlayerCatalogDao {

    private final DynamoDbAsyncTable<PlayerCatalogEntity> table;

    public PlayerCatalogDao(DynamoDbAsyncTable<PlayerCatalogEntity> playerCatalogTable) {
        this.table = playerCatalogTable;
    }

    public Mono<PlayerCatalogDto> getById(String catalogId) {
        log.info("Recupero PlayerCatalog per catalogId={}", catalogId);
        Key key = Key.builder().partitionValue(catalogId).build();
        return Mono.fromFuture(table.getItem(key))
                .map(PlayerCatalogEntityMapper::toDto)
                .doOnError(ex -> log.warn("Errore nel recupero di PlayerCatalog per catalogId={}", catalogId, ex));
    }

    /** Scan completo: usato da PlayerCatalogResolverService per la risoluzione fuzzy nome+ruolo (nessun GSI su nome). */
    public Flux<PlayerCatalogDto> findAll() {
        return Flux.from(table.scan().items())
                .map(PlayerCatalogEntityMapper::toDto)
                .doOnError(ex -> log.warn("Errore durante lo scan della tabella PlayerCatalog", ex));
    }

    public Mono<PlayerCatalogDto> save(PlayerCatalogDto dto) {
        log.info("Salvo PlayerCatalog catalogId={} nome={} ruolo={} squadra={}",
                dto.getCatalogId(), dto.getNome(), dto.getRuolo(), dto.getSquadra());
        PlayerCatalogEntity entity = PlayerCatalogEntityMapper.toEntity(dto);
        return Mono.fromFuture(table.putItem(r -> r.item(entity)))
                .thenReturn(PlayerCatalogEntityMapper.toDto(entity))
                .doOnError(ex -> log.warn("Errore nel salvataggio di PlayerCatalog catalogId={}", dto.getCatalogId(), ex));
    }
}
