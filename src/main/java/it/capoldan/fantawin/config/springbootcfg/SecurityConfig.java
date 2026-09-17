package it.capoldan.fantawin.config.springbootcfg;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.util.StringUtils;

/**
 * Autenticazione via Cognito Hosted UI (OAuth2/OIDC): questa app fa da Resource Server, valida i
 * JWT (Bearer token) che il frontend riceve da Cognito dopo il login, NON gestisce login/signup/
 * password (quelli li fa la Hosted UI - vedi infra/cloudformation per il template che crea User
 * Pool + App Client + dominio Hosted UI).
 *
 * L'autenticazione si attiva SOLO se spring.security.oauth2.resourceserver.jwt.issuer-uri e'
 * configurato (punta al tuo User Pool Cognito, es.
 * https://cognito-idp.eu-west-1.amazonaws.com/eu-west-1_xxxxxxxxx): finche' non lo valorizzi tutte
 * le richieste restano aperte come oggi, cosi' lo sviluppo/test locale continua a funzionare senza
 * dover passare da un vero User Pool. Il controllo di OWNERSHIP delle singole rose (un utente non
 * puo' leggere/modificare la rosa di un altro) e' un livello separato, in RosterService, e usa
 * AuthenticatedUserProvider per sapere chi ha chiamato - se l'auth qui e' disattivata,
 * AuthenticatedUserProvider non risolve nessun utente e quel controllo viene semplicemente saltato
 * (comportamento singolo-tenant, quello attuale).
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri) {

        http.csrf(ServerHttpSecurity.CsrfSpec::disable);

        boolean authEnabled = StringUtils.hasText(issuerUri);
        if (!authEnabled) {
            log.warn("spring.security.oauth2.resourceserver.jwt.issuer-uri non configurato: TUTTE le " +
                    "richieste restano aperte senza autenticazione (ok in locale, NON per un rilascio pubblico)");
            http.authorizeExchange(exchanges -> exchanges.anyExchange().permitAll());
            return http.build();
        }

        log.info("Autenticazione JWT (Cognito) attiva, issuer-uri={}", issuerUri);
        http.authorizeExchange(exchanges -> exchanges
                        // documentazione API e health check restano pubblici, il resto richiede un JWT valido
                        .pathMatchers("/v3/api-docs/**", "/swagger-ui/**", "/actuator/health/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                    // configurazione di default: issuer-uri sopra basta a Spring per risolvere le JWK
                    // di Cognito (endpoint .well-known/jwks.json) e validare firma/scadenza/issuer.
                }));

        return http.build();
    }
}
