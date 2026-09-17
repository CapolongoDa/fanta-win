package it.capoldan.fantawin.security;

import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Risolve l'id dell'utente autenticato (il claim "sub" del JWT Cognito) dal contesto di sicurezza
 * reattivo, per i controlli di ownership sulle rose in RosterService.
 *
 * Torna Optional.empty() in due casi ben diversi, entrambi legittimi:
 * - l'autenticazione Cognito e' disattivata (spring.security.oauth2.resourceserver.jwt.issuer-uri
 *   non configurato, vedi SecurityConfig): nessuna richiesta e' autenticata, quindi qui non c'e'
 *   proprio un contesto di sicurezza da leggere - i controlli di ownership vengono semplicemente
 *   saltati (comportamento singolo-tenant, quello attuale finche' non attivi Cognito).
 * - l'autenticazione e' attiva ma per qualche motivo il principal non e' un Jwt (non dovrebbe
 *   succedere con SecurityConfig configurato per JWT, ma non si assume mai il tipo senza controllo).
 */
@Component
public class AuthenticatedUserProvider {

    public Mono<Optional<String>> currentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication() == null ? null : ctx.getAuthentication().getPrincipal())
                .filter(Jwt.class::isInstance)
                .cast(Jwt.class)
                .map(Jwt::getSubject)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }
}
