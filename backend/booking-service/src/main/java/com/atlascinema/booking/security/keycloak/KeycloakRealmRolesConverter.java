package com.atlascinema.booking.security.keycloak;

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
 * Maps Atlas Cinema realm roles from Keycloak JWTs to Spring Security authorities.
 * Retains OAuth2 scope authorities and excludes Keycloak's internal realm roles.
 */
public class KeycloakRealmRolesConverter
        implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    private static final Set<String> APPLICATION_ROLES = Set.of(
            "CUSTOMER", "CINEMA_EMPLOYEE", "MANAGER", "ADMIN");

    private final JwtGrantedAuthoritiesConverter scopeAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

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

        if (!(realmAccessClaims.get(ROLES_CLAIM) instanceof Collection<?> realmRoles)) {
            return grantedAuthorities;
        }

        for (Object realmRole : realmRoles) {
            // Keycloak's built-in roles must never grant Atlas Cinema permissions.
            if (realmRole instanceof String roleName && APPLICATION_ROLES.contains(roleName)) {
                grantedAuthorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + roleName));
            }
        }

        return grantedAuthorities;
    }
}
