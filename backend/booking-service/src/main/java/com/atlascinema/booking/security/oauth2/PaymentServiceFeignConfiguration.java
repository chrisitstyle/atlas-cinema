
package com.atlascinema.booking.security.oauth2;

import org.springframework.cloud.openfeign.security.OAuth2AccessTokenInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

/**
 * Adds the booking-service OAuth2 access token to payment-service Feign requests.
 * Applies only to the PaymentClient Feign configuration.
 */
public class PaymentServiceFeignConfiguration {

    private static final String CLIENT_REGISTRATION_ID = "booking-service";

    @Bean
    public OAuth2AccessTokenInterceptor paymentServiceAccessTokenInterceptor(
            OAuth2AuthorizedClientManager authorizedClientManager
    ) {
        return new OAuth2AccessTokenInterceptor(
                CLIENT_REGISTRATION_ID,
                authorizedClientManager);
    }
}
