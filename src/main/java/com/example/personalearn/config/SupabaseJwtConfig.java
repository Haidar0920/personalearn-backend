package com.example.personalearn.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Validates Supabase JWTs using the JWKS endpoint (RS256).
 * No JWT secret required — uses public key cryptography.
 *
 * JWKS endpoint: https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json
 */
@Configuration
public class SupabaseJwtConfig {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Bean
    public JwtDecoder jwtDecoder() {
        String jwksUri = supabaseUrl + "/auth/v1/.well-known/jwks.json";
        return NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
    }
}
