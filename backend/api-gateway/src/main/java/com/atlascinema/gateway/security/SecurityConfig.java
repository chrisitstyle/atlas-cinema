
package com.atlascinema.gateway.security;

import com.atlascinema.gateway.security.keycloak.KeycloakRealmRolesConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Configures API Gateway security using OAuth2 Resource Server and JWT.
 *
 * Defines public endpoints, role-based access rules, and integration
 * with Keycloak-issued access tokens.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Configures authentication and authorization for incoming requests.
     *
     * @param serverHttpSecurity reactive HTTP security configuration
     * @return the configured security filter chain
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity serverHttpSecurity
    ) {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();

        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRolesConverter());

        return serverHttpSecurity
                // REST API uses Bearer tokens instead of cookie-based sessions
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)

                .authorizeExchange(authorization -> authorization

                        // Public health endpoints
                        .pathMatchers(
                                HttpMethod.GET,
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()

                        // public movie catalogue and cinema repertoire
                        .pathMatchers(
                                HttpMethod.GET,
                                "/movies", "/movies/**",
                                "/cinemas", "/cinemas/**",
                                "/screenings", "/screenings/**").permitAll()

                        // both guests and authenticated customers can book
                        .pathMatchers(HttpMethod.POST, "/bookings").permitAll()

                        // temporarily restricted to staff until booking
                        // ownership verification is implemented
                        .pathMatchers(
                                HttpMethod.PATCH,
                                "/bookings/*/cancel"
                        ).hasAnyRole("CINEMA_EMPLOYEE", "MANAGER", "ADMIN")

                        // Creating movies, cinemas, halls and screenings
                        .pathMatchers(
                                HttpMethod.POST, "/movies", "/cinemas", "/cinemas/**", "/screenings"
                        ).hasAnyRole("MANAGER", "ADMIN")

                        // Updating movie information
                        .pathMatchers(HttpMethod.PUT, "/movies/**").hasAnyRole("MANAGER", "ADMIN")

                        // changing movie and screening statuses
                        .pathMatchers(
                                HttpMethod.PATCH,
                                "/movies/**",
                                "/screenings/**"
                        ).hasAnyRole("MANAGER", "ADMIN")

                        // deny access to endpoints without explicit rules
                        .anyExchange().denyAll()
                )

                .oauth2ResourceServer(oauth2ResourceServer ->
                        oauth2ResourceServer.jwt(jwtConfiguration ->
                                jwtConfiguration.jwtAuthenticationConverter(
                                        new ReactiveJwtAuthenticationConverterAdapter(
                                                jwtAuthenticationConverter))))
                .build();
    }
}
