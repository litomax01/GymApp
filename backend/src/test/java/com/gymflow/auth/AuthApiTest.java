package com.gymflow.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymflow.support.PostgresIntegrationTest;
import com.gymflow.user.AppUser;
import com.gymflow.user.AppUserRepository;
import com.gymflow.user.Role;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

class AuthApiTest extends PostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void seed() {
        if (users.findByEmailIgnoreCase("auth@gymflow.test").isEmpty()) {
            users.save(new AppUser("auth@gymflow.test", encoder.encode("Secreta123!"), "Usuario Auth", Role.ADMIN));
        }
    }

    private static String body(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    @Test
    void loginCorrectoDevuelveTokenUsableEnEndpointsProtegidos() throws Exception {
        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body("AUTH@gymflow.test", "Secreta123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String token = com.jayway.jsonpath.JsonPath.read(json, "$.accessToken");

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("auth@gymflow.test"));
    }

    @Test
    void passwordIncorrectaDa401ConMensajeGenerico() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body("auth@gymflow.test", "mala")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void emailInexistenteDaLaMismaRespuestaQuePasswordIncorrecta() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body("nadie@gymflow.test", "Secreta123!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email o contraseña incorrectos"));
    }

    @Test
    void sinTokenOTokenInvalidoDa401() throws Exception {
        mvc.perform(get("/api/members")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/members").header(HttpHeaders.AUTHORIZATION, "Bearer basura"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginConEmailInvalidoDa400ConDetallePorCampo() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body("no-es-email", "x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].field").value("email"));
    }
}
