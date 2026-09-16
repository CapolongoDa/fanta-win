package it.capoldan.fantawin.middleware.dao.dynamo;

import reactor.core.publisher.Mono;

/**
 * Contratto comune dei DAO DynamoDB: operazioni di scrittura indipendenti
 * dalla struttura della chiave primaria (semplice o composta).
 *
 * @param <D> tipo del DTO gestito dal DAO
 */
public interface BaseDao<D> {

    /** Crea o sovrascrive completamente l'item. */
    Mono<D> save(D dto);

    /** Aggiornamento parziale: i campi null nel dto non sovrascrivono i valori esistenti. */
    Mono<D> update(D dto);
}
