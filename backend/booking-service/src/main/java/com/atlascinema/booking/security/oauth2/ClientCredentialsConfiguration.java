
package com.atlascinema.booking.security.oauth2;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Manages OAuth2 Client Credentials tokens for booking-service
 * Supports service authentication without requiring an HTTP user session
 */
@Configuration
public class ClientCredentialsConfiguration {

    @Bean
    public OAuth2AuthorizedClientManager oauth2AuthorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService
    ) {
        OAuth2AuthorizedClientProvider clientCredentialsProvider = OAuth2AuthorizedClientProviderBuilder
                        .builder()
                        .clientCredentials()
                        .build();

        AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrationRepository,
                        authorizedClientService);

        // reuse valid tokens and request replacements when they expire
        authorizedClientManager.setAuthorizedClientProvider(clientCredentialsProvider);

        return authorizedClientManager;
    }
}
