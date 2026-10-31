
package com.atlascinema.cinema.security.keycloak;

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
 * Converts Keycloak realm roles into Spring Security authorities.
 * Preserves OAuth2 scope authorities and ignores Keycloak-specific internal roles.
 */
public class KeycloakRealmRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    private static final Set<String> APPLICATION_ROLES = Set.of(
            "CUSTOMER",
            "CINEMA_EMPLOYEE",
            "MANAGER",
            "ADMIN");

    private final JwtGrantedAuthoritiesConverter scopeAuthoritiesConverter =
            new JwtGrantedAuthoritiesConverter();

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> grantedAuthorities = new HashSet<>();

        Collection<GrantedAuthority> scopeAuthorities = scopeAuthoritiesConverter.convert(jwt);

        if (scopeAuthorities != null) {
            grantedAuthorities.addAll(scopeAuthorities);
        }

        Map<String, Object> realmAccessClaims = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);

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

            // only application roles may grant permissions in Atlas Cinema.
            if (APPLICATION_ROLES.contains(roleName)) {
                grantedAuthorities.add(
                        new SimpleGrantedAuthority(ROLE_PREFIX + roleName));
            }
        }

        return grantedAuthorities;
    }
}
