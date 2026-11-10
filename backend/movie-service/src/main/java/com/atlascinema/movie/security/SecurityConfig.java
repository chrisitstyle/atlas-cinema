
package com.atlascinema.movie.security;

import com.atlascinema.movie.security.keycloak.KeycloakRealmRolesConverter;
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
 * Secures movie-service HTTP endpoints using Keycloak JWT access tokens.
 * Allows public movie reads and restricts catalogue management to managers and admins.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRolesConverter());

        httpSecurity
                // this stateless API authenticates with Bearer tokens, not browser sessions.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(sessionManagement -> sessionManagement
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(authorization -> authorization
                        // preserve Spring MVC error responses on internal ERROR dispatches.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                        .permitAll()

                        // public reads are also used by screening-service through Feign.
                        .requestMatchers(HttpMethod.GET, "/movies", "/movies/*")
                        .permitAll()

                        .requestMatchers(HttpMethod.POST, "/movies")
                        .hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/movies/*")
                        .hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH,
                                "/movies/*/discontinue", "/movies/*/restore")
                        .hasAnyRole("MANAGER", "ADMIN")

                        // new endpoints remain inaccessible until explicitly authorized
                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(oauth2ResourceServer -> oauth2ResourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return httpSecurity.build();
    }
}
