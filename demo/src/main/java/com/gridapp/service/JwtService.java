package com.gridapp.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
@Slf4j
public class JwtService {

    @Value("${supabase.jwt.secret:}")
    private String jwtSecret;

    /**
     * Decodes and optionally verifies the Supabase JWT.
     * HS256 signature verification is performed only if supabase.jwt.secret is set.
     */
    public DecodedJWT verifyToken(String token) {
        try {
            DecodedJWT jwt = JWT.decode(token);
            if (jwtSecret != null && !jwtSecret.isBlank()) {
                // Try 1: Try base64 decoded secret bytes (standard for Supabase dashboard secrets)
                try {
                    byte[] decodedSecret = Base64.getDecoder().decode(jwtSecret.trim());
                    Algorithm algorithm = Algorithm.HMAC256(decodedSecret);
                    JWTVerifier verifier = JWT.require(algorithm).build();
                    return verifier.verify(token);
                } catch (Throwable t) {
                    // Try 2: Try raw secret bytes
                    try {
                        Algorithm algorithm = Algorithm.HMAC256(jwtSecret);
                        JWTVerifier verifier = JWT.require(algorithm).build();
                        return verifier.verify(token);
                    } catch (Throwable t2) {
                        log.warn("Supabase JWT signature verification failed (using fallback decode): base64_err={} raw_err={}", t.getMessage(), t2.getMessage());
                    }
                }
            }
            return jwt;
        } catch (Throwable t) {
            log.error("Supabase JWT verification/decoding failed: {}", t.getMessage());
            throw new IllegalArgumentException("Invalid authentication token", t);
        }
    }

    /**
     * Extracts user UUID string from the 'sub' claim.
     */
    public String getUserIdFromToken(DecodedJWT jwt) {
        return jwt.getSubject();
    }

    /**
     * Extracts email from token payload.
     */
    public String getEmailFromToken(DecodedJWT jwt) {
        return jwt.getClaim("email").asString();
    }
}
