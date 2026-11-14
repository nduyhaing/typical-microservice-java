package com.shopapp.backofficebff;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopapp.backofficebff.config.SecurityConfig;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

/**
 * Keycloak puts realm roles in {@code realm_access.roles} of the user info; the BFF turns them into ROLE_*.
 */
class KeycloakRolesMappingTest {

    private final GrantedAuthoritiesMapper mapper = new SecurityConfig(null).userAuthoritiesMapperForKeycloak();

    private static OidcIdToken idToken(Map<String, Object> claims) {
        OidcIdToken.Builder builder = OidcIdToken.withTokenValue("id-token")
            .subject("user-1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(builder::claim);
        return builder.build();
    }

    private static List<String> names(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void realmRolesFromUserInfo_becomeRoleAuthorities() {
        OidcUserInfo userInfo = new OidcUserInfo(Map.of("sub", "user-1",
            "realm_access", Map.of("roles", List.of("ADMIN", "offline_access"))));

        var mapped = mapper.mapAuthorities(List.of(new OidcUserAuthority(idToken(Map.of()), userInfo)));

        assertThat(names(mapped)).contains("ROLE_ADMIN", "ROLE_offline_access");
    }

    @Test
    void realmRolesFromIdToken_whenNoUserInfo() {
        var token = idToken(Map.of("realm_access", Map.of("roles", List.of("ADMIN"))));

        var mapped = mapper.mapAuthorities(List.of(new OidcUserAuthority(token)));

        assertThat(names(mapped)).contains("ROLE_ADMIN");
    }

    @Test
    void noRealmAccess_noRoleAuthorities() {
        var mapped = mapper.mapAuthorities(List.of(new OidcUserAuthority(idToken(Map.of()))));

        assertThat(names(mapped)).noneMatch(name -> name.startsWith("ROLE_"));
    }
}
