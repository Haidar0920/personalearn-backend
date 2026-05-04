package com.example.personalearn.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converts Supabase JWT to Spring Security authentication token.
 *
 * Supabase JWT structure:
 * - sub: user UUID
 * - email: user email
 * - app_metadata.role: "client_admin" | "client_user"
 * - user_metadata: custom user fields
 */
@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        String principal = jwt.getSubject(); // Supabase user UUID
        return new JwtAuthenticationToken(jwt, authorities, principal);
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        // Supabase stores custom roles in app_metadata.role
        Map<String, Object> appMetadata = jwt.getClaim("app_metadata");
        if (appMetadata == null) {
            return defaultAuthorities();
        }

        Object roleObj = appMetadata.get("role");
        if (roleObj == null) {
            return defaultAuthorities();
        }

        // role can be a String or a List<String>
        if (roleObj instanceof String role) {
            return Set.of(new SimpleGrantedAuthority("ROLE_" + role));
        }

        if (roleObj instanceof List<?> roles) {
            return roles.stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .collect(Collectors.toSet());
        }

        return defaultAuthorities();
    }

    private Collection<GrantedAuthority> defaultAuthorities() {
        return Set.of(new SimpleGrantedAuthority("ROLE_client_user"));
    }
}
