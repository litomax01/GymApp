package com.gymflow.member;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymflow.support.PostgresIntegrationTest;
import com.gymflow.support.TestAuth;
import com.gymflow.user.Role;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class MemberApiTest extends PostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestAuth auth;
    @Autowired com.gymflow.support.TestDb db;

    String token;
    String adminToken;

    @BeforeEach
    void setUp() {
        db.reset();
        token = auth.bearer("recepcion@gymflow.test", Role.RECEPTION);
        adminToken = auth.bearer("admin@gymflow.test", Role.ADMIN);
    }

    private static String member(String doc, String first, String last) {
        return """
                {"documentNumber":"%s","firstName":"%s","lastName":"%s","email":"%s@mail.com","phone":"+34 600 111 222"}
                """.formatted(doc, first, last, first.toLowerCase());
    }

    private ResultActions create(String json) throws Exception {
        return mvc.perform(post("/api/members").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void creaSocioYLoRecuperaPorId() throws Exception {
        String json = create(member("12345678", "Lucía", "Pérez")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        int id = com.jayway.jsonpath.JsonPath.read(json, "$.id");

        mvc.perform(get("/api/members/" + id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Pérez"));
    }

    @Test
    void rechazaDocumentoDuplicadoConConflicto() throws Exception {
        create(member("111", "Ana", "Uno")).andExpect(status().isCreated());
        create(member("111", "Beto", "Dos")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_DOCUMENT"));
    }

    @Test
    void validaCamposObligatoriosYFormato() throws Exception {
        create("{\"documentNumber\":\"\",\"firstName\":\"\",\"lastName\":\"X\",\"email\":\"mal\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.length()").value(3));
    }

    @Test
    void rechazaCamposDesconocidos() throws Exception {
        create("{\"documentNumber\":\"9\",\"firstName\":\"A\",\"lastName\":\"B\",\"active\":false,\"id\":99}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void buscaPorNombreParcialYPagina() throws Exception {
        create(member("1", "María", "García")).andExpect(status().isCreated());
        create(member("2", "Mario", "López")).andExpect(status().isCreated());
        create(member("3", "Pedro", "Sánchez")).andExpect(status().isCreated());

        mvc.perform(get("/api/members").param("q", "mar").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].lastName").value("García"));
        mvc.perform(get("/api/members").param("size", "2").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void actualizaPeroNoPermiteTomarDocumentoDeOtro() throws Exception {
        create(member("10", "Ana", "Uno")).andExpect(status().isCreated());
        String json = create(member("20", "Beto", "Dos")).andReturn().getResponse().getContentAsString();
        int id = com.jayway.jsonpath.JsonPath.read(json, "$.id");

        mvc.perform(put("/api/members/" + id).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(member("10", "Beto", "Dos")))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/members/" + id).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(member("20", "Beto", "Renombrado")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Renombrado"));
    }

    @Test
    void desactivarEsBajaLogicaYSeOcultaPorDefecto() throws Exception {
        String json = create(member("30", "Eva", "Baja")).andReturn().getResponse().getContentAsString();
        int id = com.jayway.jsonpath.JsonPath.read(json, "$.id");

        mvc.perform(patch("/api/members/" + id + "/deactivate").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden()); // recepción no puede dar de baja
        mvc.perform(patch("/api/members/" + id + "/deactivate").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/members").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/members").param("onlyActive", "false").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void socioInexistenteDa404() throws Exception {
        mvc.perform(get("/api/members/999999").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }
}
