package com.gymflow.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Usuarios de demostración. Solo se ejecuta con el perfil "demo"; nunca en producción. */
@Component
@Profile("demo")
@Order(1)
class DemoUserSeeder implements ApplicationRunner {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final String password;

    DemoUserSeeder(AppUserRepository users, PasswordEncoder encoder,
                   @Value("${gymflow.demo.admin-password}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed("admin@gymflow.test", "Ana Administradora", Role.ADMIN);
        seed("recepcion@gymflow.test", "Raúl Recepción", Role.RECEPTION);
    }

    private void seed(String email, String name, Role role) {
        if (users.findByEmailIgnoreCase(email).isEmpty()) {
            users.save(new AppUser(email, encoder.encode(password), name, role));
        }
    }
}
