
package com.atlascinema.payment.security.keycloak;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Authorizes payment initiation requests from booking-service
 * Requires the expected OAuth2 client identity and technical realm role
 */
public class BookingServiceAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final String BOOKING_SERVICE_CLIENT_ID = "atlas-cinema-booking-service";

    private static final String REQUIRED_REALM_ROLE = "SERVICE_PAYMENT_INITIATE";

    private static final String AUTHORIZED_PARTY_CLAIM = "azp";
    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM = "roles";

    @Override
    public AuthorizationDecision authorize(
            Supplier<? extends Authentication> authenticationSupplier,
            RequestAuthorizationContext authorizationContext
    ) {
        Authentication authentication = authenticationSupplier.get();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return new AuthorizationDecision(false);
        }

        Jwt accessToken = jwtAuthentication.getToken();

        boolean isBookingService = hasExpectedClientIdentity(accessToken);
        boolean hasRequiredRole = hasRequiredRealmRole(accessToken);

        // both client identity and technical permission must match
        return new AuthorizationDecision(isBookingService && hasRequiredRole);
    }

    private boolean hasExpectedClientIdentity(Jwt accessToken) {
        String authorizedClientId = accessToken.getClaimAsString(AUTHORIZED_PARTY_CLAIM);

        return BOOKING_SERVICE_CLIENT_ID.equals(authorizedClientId);
    }

    private boolean hasRequiredRealmRole(Jwt accessToken) {
        Map<String, Object> realmAccessClaims = accessToken.getClaimAsMap(REALM_ACCESS_CLAIM);

        if (realmAccessClaims == null) {
            return false;
        }

        Object realmRolesClaim = realmAccessClaims.get(ROLES_CLAIM);

        if (!(realmRolesClaim instanceof Collection<?> realmRoles)) {
            return false;
        }

        return realmRoles.contains(REQUIRED_REALM_ROLE);
    }
}
