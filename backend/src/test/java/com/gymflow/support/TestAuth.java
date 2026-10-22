package com.gymflow.support;

import com.gymflow.security.JwtService;
import com.gymflow.user.AppUser;
import com.gymflow.user.AppUserRepository;
import com.gymflow.user.Role;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Crea usuarios de prueba y emite tokens Bearer reales. */
@Component
public class TestAuth {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    TestAuth(AppUserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public String bearer(String email, Role role) {
        AppUser u = users.findByEmailIgnoreCase(email)
                .orElseGet(() -> users.save(new AppUser(email, encoder.encode("Test1234!"), "Test " + role, role)));
        return "Bearer " + jwt.issue(u).value();
    }
}
