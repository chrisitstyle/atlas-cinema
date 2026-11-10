
package com.atlascinema.booking.security.keycloak;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.function.Supplier;

/**
 * Authorizes payment-service access to internal booking payment details.
 * Requires both the technical permission and the expected OAuth2 client identity.
 */
public class PaymentServiceAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    private static final String PAYMENT_SERVICE_CLIENT_ID = "atlas-cinema-payment-service";

    private static final String REQUIRED_AUTHORITY = "ROLE_SERVICE_PAYMENT_DETAILS_READ";

    private static final String AUTHORIZED_PARTY_CLAIM = "azp";

    @Override
    public AuthorizationDecision authorize(
            Supplier<? extends Authentication> authenticationSupplier,
            RequestAuthorizationContext authorizationContext
    ) {
        Authentication authentication = authenticationSupplier.get();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return new AuthorizationDecision(false);
        }

        String authorizedClientId = jwtAuthentication.getToken()
                .getClaimAsString(AUTHORIZED_PARTY_CLAIM);

        boolean isPaymentService = PAYMENT_SERVICE_CLIENT_ID.equals(authorizedClientId);

        boolean hasRequiredAuthority = jwtAuthentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        REQUIRED_AUTHORITY.equals(authority.getAuthority()));

        // require both the expected client identity and technical permission
        return new AuthorizationDecision(isPaymentService && hasRequiredAuthority);
    }
}
