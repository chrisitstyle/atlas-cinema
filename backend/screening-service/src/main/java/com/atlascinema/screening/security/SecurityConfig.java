
package com.atlascinema.screening.security;

import com.atlascinema.screening.security.keycloak.KeycloakRealmRolesConverter;
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
 * Configures JWT authentication and role-based authorization for screening-service.
 * Keeps repertoire endpoints public and restricts screening management to managers and admins.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configures security rules for screening-service HTTP requests.
     *
     * @param httpSecurity HTTP security configuration
     * @return configured security filter chain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity httpSecurity
    ) throws Exception {

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();

        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRolesConverter());

        httpSecurity
                // Authenticate requests using JWT without HTTP sessions.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(sessionManagement -> sessionManagement
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(authorization -> authorization

                        // allow internal error dispatches to preserve MVC error responses
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                        // public health endpoints
                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()

                        // public repertoire endpoints used by customers and booking-service
                        .requestMatchers(
                                HttpMethod.GET,
                                "/screenings",
                                "/screenings/*"
                        ).permitAll()

                        // only managers and admins can create screenings
                        .requestMatchers(
                                HttpMethod.POST,
                                "/screenings"
                        ).hasAnyRole("MANAGER", "ADMIN")

                        // only managers and admins can cancel screenings
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/screenings/*/cancel"
                        ).hasAnyRole("MANAGER", "ADMIN")

                        // deny all endpoints without explicit authorization rules
                        .anyRequest().denyAll()
                )

                .oauth2ResourceServer(oauth2ResourceServer ->
                        oauth2ResourceServer.jwt(jwt ->
                                jwt.jwtAuthenticationConverter(
                                        jwtAuthenticationConverter)));

        return httpSecurity.build();
    }
}
