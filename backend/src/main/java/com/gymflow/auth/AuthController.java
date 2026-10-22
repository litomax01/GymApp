package com.gymflow.auth;

import java.time.Instant;

import com.gymflow.common.ApiError;
import com.gymflow.common.NotFoundException;
import com.gymflow.security.CurrentUser;
import com.gymflow.security.JwtService;
import com.gymflow.user.AppUser;
import com.gymflow.user.AppUserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    // Hash de relleno: iguala el tiempo de respuesta cuando el email no existe (evita enumerar usuarios).
    private final String dummyHash;

    AuthController(AppUserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.dummyHash = encoder.encode("relleno-no-usado");
    }

    record LoginRequest(@NotBlank @Email @Size(max = 160) String email, @NotBlank @Size(max = 100) String password) {}

    record UserView(Long id, String email, String fullName, String role) {
        static UserView of(AppUser u) {
            return new UserView(u.getId(), u.getEmail(), u.getFullName(), u.getRole().name());
        }
    }

    record LoginResponse(String accessToken, Instant expiresAt, UserView user) {}

    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        AppUser user = users.findByEmailIgnoreCase(req.email()).orElse(null);
        boolean matches = encoder.matches(req.password(), user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !matches || !user.isActive()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiError.of(401, "BAD_CREDENTIALS", "Email o contraseña incorrectos"));
        }
        var token = jwt.issue(user);
        return ResponseEntity.ok(new LoginResponse(token.value(), token.expiresAt(), UserView.of(user)));
    }

    @GetMapping("/me")
    UserView me() {
        long id = CurrentUser.id();
        return users.findById(id).map(UserView::of).orElseThrow(() -> new NotFoundException("Usuario", id));
    }
}
