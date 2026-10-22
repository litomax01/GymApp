package com.gymflow.security;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gymflow.security")
public record SecurityProperties(String jwtSecret, long jwtTtlMinutes) {

    public SecurityProperties {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET es obligatorio y debe tener al menos 32 caracteres (gymflow.security.jwt-secret)");
        }
    }
}
