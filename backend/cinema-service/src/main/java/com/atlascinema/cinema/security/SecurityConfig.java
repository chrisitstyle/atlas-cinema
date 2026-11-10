
package com.atlascinema.cinema.security;

import com.atlascinema.cinema.security.keycloak.KeycloakRealmRolesConverter;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Secures cinema-service endpoints using Keycloak access tokens.
 * Allows public cinema reads and restricts cinema configuration to managers and admins.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity httpSecurity
    ) throws Exception {

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();

        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRolesConverter());

        httpSecurity
                // stateless APIs authenticate through Bearer tokens instead of HTTP sessions.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(sessionManagement -> sessionManagement
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorization -> authorization

                        // preserve MVC error responses during internal ERROR dispatches.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()

                        // public cinema, hall, seat and structure reads.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/cinemas",
                                "/cinemas/*",
                                "/cinemas/*/halls",
                                "/cinemas/*/halls/*/seats",
                                "/cinemas/*/halls/*/seats/*",
                                "/cinemas/*/structure"
                        ).permitAll()

                        // temporary: existing Feign clients have no service-to-service credentials.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/internal/halls/*",
                                "/internal/halls/*/seats/*"
                        ).permitAll()

                        // managing cinemas, halls and seats requires staff privileges.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/cinemas",
                                "/cinemas/*/halls",
                                "/cinemas/*/halls/*/seats"
                        ).hasAnyRole("MANAGER", "ADMIN")

                        // all new routes are denied until reviewed explicitly.
                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(oauth2ResourceServer -> oauth2ResourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                jwtAuthenticationConverter)));

        return httpSecurity.build();
    }
}
