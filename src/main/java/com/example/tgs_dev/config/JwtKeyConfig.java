package com.example.tgs_dev.config;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;

@Configuration
public class JwtKeyConfig {

    @Value("${app.jwt.secret}")
    private String secret;

    @Bean
    public SecretKey jwtSecretKey() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "TGS_JWT_SECRET is not set. Refusing to start with an empty JWT signing key. "
                  + "Generate one with: openssl rand -base64 64");
        }
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "TGS_JWT_SECRET is not a valid Base64 string. "
                  + "Generate one with: openssl rand -base64 64", ex);
        }
        if (keyBytes.length < 64) {
            throw new IllegalStateException(
                    "TGS_JWT_SECRET decoded to " + keyBytes.length + " bytes; "
                  + "HS512 requires at least 64 bytes (512 bits).");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}