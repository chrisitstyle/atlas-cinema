package com.atlascinema.booking.security;

import com.atlascinema.booking.security.keycloak.KeycloakRealmRolesConverter;
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
 * Configures JWT authentication and endpoint authorization for booking-service.
 * Allows guest checkout while protecting customer history and staff cancellation.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(
                new KeycloakRealmRolesConverter()
        );

        httpSecurity
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(sessionManagement -> sessionManagement
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(authorization -> authorization
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                        .permitAll()

                        // guests and authenticated users can purchase tickets
                        .requestMatchers(HttpMethod.POST, "/bookings").permitAll()

                        // customers may list only bookings linked to their jwt subject
                        .requestMatchers(HttpMethod.GET, "/bookings/me").hasRole("CUSTOMER")

                        // cancellation remains staff-only until guest/owner cancellation is designed
                        .requestMatchers(HttpMethod.PATCH, "/bookings/*/cancel")
                        .hasAnyRole("CINEMA_EMPLOYEE", "MANAGER", "ADMIN")

                        // temporary compatibility for payment-service Feign. NOT yet service-authenticated
                        .requestMatchers(HttpMethod.GET, "/internal/bookings/*/payment-details")
                        .permitAll()

                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(oauth2ResourceServer -> oauth2ResourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                );

        return httpSecurity.build();
    }
}
