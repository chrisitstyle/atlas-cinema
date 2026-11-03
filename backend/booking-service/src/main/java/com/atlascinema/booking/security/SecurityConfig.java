
package com.atlascinema.booking.security;

import com.atlascinema.booking.security.keycloak.KeycloakRealmRolesConverter;
import com.atlascinema.booking.security.keycloak.PaymentServiceAuthorizationManager;
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
 * Configures JWT authentication and endpoint authorization for booking-service
 * Supports guest checkout, customer booking history and authenticated service communication
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();

        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(
                new KeycloakRealmRolesConverter());

        httpSecurity
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(sessionManagement -> sessionManagement
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(authorization -> authorization

                        // preserve Spring MVC error responses
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                        // public health endpoints
                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()

                        // both guests and authenticated customers can create bookings
                        .requestMatchers(HttpMethod.POST, "/bookings")
                        .permitAll()

                        // customers can access their own booking history
                        .requestMatchers(HttpMethod.GET, "/bookings/me")
                        .hasRole("CUSTOMER")

                        // cancellation remains staff-only until ownership checks are implemented
                        .requestMatchers(HttpMethod.PATCH, "/bookings/*/cancel")
                        .hasAnyRole("CINEMA_EMPLOYEE", "MANAGER", "ADMIN")

                        // only the authorized payment-service can read payment details
                        .requestMatchers(
                                HttpMethod.GET,
                                "/internal/bookings/*/payment-details"
                        ).access(new PaymentServiceAuthorizationManager())

                        // reject endpoints without explicit authorization rules.
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2ResourceServer ->
                        oauth2ResourceServer.jwt(jwt ->
                                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return httpSecurity.build();
    }
}
