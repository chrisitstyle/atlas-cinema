
package com.atlascinema.gateway.security.keycloak;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Converts Keycloak realm roles from JWT claims into Spring Security authorities.
 *
 * Preserves standard OAuth2 scope authorities and maps only roles
 * explicitly defined by Atlas Cinema.
 */
public class KeycloakRealmRolesConverter
        implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    private static final Set<String> APPLICATION_ROLES = Set.of(
            "CUSTOMER",
            "CINEMA_EMPLOYEE",
            "MANAGER",
            "ADMIN");

    private final JwtGrantedAuthoritiesConverter scopeAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

    /**
     * Extracts OAuth2 scopes and application realm roles from a JWT.
     *
     * @param jwt the validated JWT issued by Keycloak
     * @return Spring Security authorities derived from the token
     */
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> grantedAuthorities = new HashSet<>();

        // preserve standard OAuth2 scope authorities (SCOPE_*).
        Collection<GrantedAuthority> scopeAuthorities = scopeAuthoritiesConverter.convert(jwt);

        if (scopeAuthorities != null) {
            grantedAuthorities.addAll(scopeAuthorities);
        }

        Map<String, Object> realmAccessClaims =
                jwt.getClaimAsMap(REALM_ACCESS_CLAIM);

        if (realmAccessClaims == null) {
            return grantedAuthorities;
        }

        Object realmRolesClaim = realmAccessClaims.get(ROLES_CLAIM);

        if (!(realmRolesClaim instanceof Collection<?> realmRoles)) {
            return grantedAuthorities;
        }

        for (Object realmRole : realmRoles) {
            if (!(realmRole instanceof String roleName)) {
                continue;
            }

            // ignore Keycloak internal roles such as offline_access.
            // only Atlas Cinema roles can grant application permissions.
            if (!APPLICATION_ROLES.contains(roleName)) {
                continue;
            }

            grantedAuthorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + roleName));
        }

        return grantedAuthorities;
    }
}
