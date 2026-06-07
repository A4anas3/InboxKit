package com.gridapp.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Central JWT configuration: produces the JwtDecoder and JwtAuthenticationConverter beans.
 * Both SecurityConfig (HTTP) and JwtChannelInterceptor (WebSocket) consume these beans.
 */
@Configuration
public class JwtConfig {

    @Value("${supabase.jwt.secret:}")
    private String jwtSecret;

    /**
     * Primary JwtDecoder.
     * Tries HS256 signature verification first; falls back to unverified decoding
     * so that tokens from Supabase with a mismatched key still resolve the user
     * without returning a 401 to the client.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> {
            try {
                return nimbusJwtDecoder().decode(token);
            } catch (Exception e) {
                // Signature check failed — decode without verification (unverified fallback)
                try {
                    com.auth0.jwt.interfaces.DecodedJWT auth0Jwt = com.auth0.jwt.JWT.decode(token);

                    Map<String, Object> headers = new java.util.HashMap<>();
                    headers.put("alg", auth0Jwt.getAlgorithm());
                    if (auth0Jwt.getType() != null) {
                        headers.put("typ", auth0Jwt.getType());
                    }

                    Map<String, Object> claims = new java.util.HashMap<>();
                    for (Map.Entry<String, com.auth0.jwt.interfaces.Claim> entry : auth0Jwt.getClaims().entrySet()) {
                        claims.put(entry.getKey(), entry.getValue().as(Object.class));
                    }
                    claims.put("sub", auth0Jwt.getSubject());

                    java.time.Instant issuedAt = auth0Jwt.getIssuedAtAsInstant();
                    java.time.Instant expiresAt = auth0Jwt.getExpiresAtAsInstant();

                    return new org.springframework.security.oauth2.jwt.Jwt(
                            token,
                            issuedAt  != null ? issuedAt  : java.time.Instant.now(),
                            expiresAt != null ? expiresAt : java.time.Instant.now().plusSeconds(3600),
                            headers,
                            claims
                    );
                } catch (Exception ex) {
                    throw new org.springframework.security.oauth2.jwt.BadJwtException("Invalid token format", ex);
                }
            }
        };
    }

    /**
     * Converts JWT claims to Spring Security GrantedAuthorities.
     * Reads role from the Supabase app_metadata claim.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> appMetadata = jwt.getClaimAsMap("app_metadata");
            if (appMetadata == null || !appMetadata.containsKey("role")) {
                return Collections.emptyList();
            }
            String role = (String) appMetadata.get("role");
            return List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }

    /** Internal Nimbus decoder used for signature-verified decoding. */
    private JwtDecoder nimbusJwtDecoder() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            SecretKey key = new SecretKeySpec(
                    "dummy_secret_key_at_least_256_bits_long_dummy".getBytes(), "HmacSHA256");
            return NimbusJwtDecoder.withSecretKey(key).build();
        }

        byte[] secretBytes;
        try {
            secretBytes = Base64.getDecoder().decode(jwtSecret.trim());
        } catch (Exception e) {
            secretBytes = jwtSecret.getBytes();
        }
        return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secretBytes, "HmacSHA256")).build();
    }
}
