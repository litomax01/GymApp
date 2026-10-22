package com.gymflow.security;

import java.time.Duration;
import java.time.Instant;

import com.gymflow.user.AppUser;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final Duration ttl;

    JwtService(JwtEncoder encoder, SecurityProperties props) {
        this.encoder = encoder;
        this.ttl = Duration.ofMinutes(props.jwtTtlMinutes());
    }

    public IssuedToken issue(AppUser user) {
        Instant now = Instant.now();
        Instant exp = now.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("gymflow")
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(exp)
                .claim("role", user.getRole().name())
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, exp);
    }

    public record IssuedToken(String value, Instant expiresAt) {}
}
