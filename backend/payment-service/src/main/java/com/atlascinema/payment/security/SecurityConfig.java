
package com.atlascinema.payment.security;

import com.atlascinema.payment.security.keycloak.BookingServiceAuthorizationManager;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configures JWT authentication and authorization for payment-service
 * Restricts payment initiation to the authorized booking-service client
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity
    ) throws Exception {

        httpSecurity
                //use stateless Bearer authentication instead of HTTP sessions
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(sessionManagement -> sessionManagement
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(authorization -> authorization

                        // p*reserve Spring MVC error responses
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                        // public health endpoints
                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()

                        // only booking-service may initiate payments
                        .requestMatchers(
                                HttpMethod.POST,
                                "/internal/payments"
                        ).access(new BookingServiceAuthorizationManager())

                        // deny all endpoints without explicit authorization rules
                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(oauth2ResourceServer ->
                        oauth2ResourceServer.jwt(Customizer.withDefaults())
                );

        return httpSecurity.build();
    }
}
