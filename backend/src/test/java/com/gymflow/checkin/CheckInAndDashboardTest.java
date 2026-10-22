package com.gymflow.checkin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import com.gymflow.support.PostgresIntegrationTest;
import com.gymflow.support.TestAuth;
import com.gymflow.support.TestDb;
import com.gymflow.user.Role;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class CheckInAndDashboardTest extends PostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestAuth auth;
    @Autowired TestDb db;
    @Autowired JdbcClient jdbc;

    String admin;
    String reception;
    int planId;

    @BeforeEach
    void setUp() throws Exception {
        db.reset();
        admin = auth.bearer("admin@gymflow.test", Role.ADMIN);
        reception = auth.bearer("recepcion@gymflow.test", Role.RECEPTION);
        planId = send(post("/api/plans"), admin, "{\"name\":\"Mensual\",\"price\":30.00,\"durationDays\":30}");
    }

    private ResultActions raw(MockHttpServletRequestBuilder req, String token, String json) throws Exception {
        return mvc.perform(req.header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private int id(ResultActions r) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private int send(MockHttpServletRequestBuilder req, String token, String json) throws Exception {
        return id(raw(req, token, json));
    }

    private int newMember(String doc, String first) throws Exception {
        return send(post("/api/members"), reception,
                "{\"documentNumber\":\"%s\",\"firstName\":\"%s\",\"lastName\":\"Test\",\"phone\":\"600\"}".formatted(doc, first));
    }

    private void contract(int memberId, String startDate) throws Exception {
        raw(post("/api/members/" + memberId + "/memberships"), reception,
                "{\"planId\":%d,\"startDate\":\"%s\",\"payment\":{\"amount\":30,\"method\":\"CASH\"}}".formatted(planId, startDate))
                .andExpect(status().isCreated());
    }

    private ResultActions checkIn(int memberId) throws Exception {
        return raw(post("/api/check-ins"), reception, "{\"memberId\":%d}".formatted(memberId));
    }

    @Test
    void permiteConMembresiaVigenteYMuestraDiasRestantes() throws Exception {
        int m = newMember("1", "Vigente");
        contract(m, LocalDate.now().minusDays(5).toString());
        checkIn(m).andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("ALLOWED"))
                .andExpect(jsonPath("$.daysLeft").value(24));
    }

    @Test
    void deniegaYRegistraMotivoSinMembresiaOVencida() throws Exception {
        int sin = newMember("2", "Sin");
        checkIn(sin).andExpect(status().isOk()).andExpect(jsonPath("$.result").value("DENIED"))
                .andExpect(jsonPath("$.reason").value("NO_MEMBERSHIP"));

        int vencido = newMember("3", "Vencido");
        contract(vencido, LocalDate.now().minusDays(31).toString()); // termina hace 2 días
        checkIn(vencido).andExpect(jsonPath("$.reason").value("MEMBERSHIP_EXPIRED"));

        int futuro = newMember("4", "Futuro");
        contract(futuro, LocalDate.now().plusDays(3).toString());
        checkIn(futuro).andExpect(jsonPath("$.result").value("DENIED"));

        mvc.perform(get("/api/check-ins/recent").header(HttpHeaders.AUTHORIZATION, reception))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].memberName").exists());
    }

    @Test
    void socioDeBajaYSocioInexistente() throws Exception {
        int m = newMember("5", "Baja");
        contract(m, LocalDate.now().toString());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/members/" + m + "/deactivate").header(HttpHeaders.AUTHORIZATION, admin));
        checkIn(m).andExpect(jsonPath("$.reason").value("MEMBER_INACTIVE"));
        checkIn(999999).andExpect(status().isNotFound());
    }

    @Test
    void dashboardCuentaRadarYOcultaDineroAlRecepcionista() throws Exception {
        int ok = newMember("10", "Activo");          // vigente y con visita hoy
        contract(ok, LocalDate.now().minusDays(2).toString());
        checkIn(ok);

        int porVencer = newMember("11", "PorVencer"); // vence en 3 días, visita reciente
        contract(porVencer, LocalDate.now().minusDays(26).toString());
        checkIn(porVencer);

        int inactivo = newMember("12", "Inactivo");   // vigente pero última visita hace 20 días
        contract(inactivo, LocalDate.now().minusDays(15).toString());
        jdbc.sql("insert into check_ins (member_id, checked_in_at, result, registered_by) select :m, now() - interval '20 days', 'ALLOWED', min(id) from app_users")
                .param("m", (long) inactivo).update();

        newMember("13", "SinMembresia");

        mvc.perform(get("/api/dashboard").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeMembers").value(3))
                .andExpect(jsonPath("$.expiringSoon").value(1))
                .andExpect(jsonPath("$.checkInsToday").value(2))
                .andExpect(jsonPath("$.monthRevenue").value(90.0))
                .andExpect(jsonPath("$.atRisk.length()").value(2))
                .andExpect(jsonPath("$.atRisk[0].reason").value("INACTIVE"))
                .andExpect(jsonPath("$.atRisk[0].memberId").value(inactivo))
                .andExpect(jsonPath("$.atRisk[1].reason").value("EXPIRING"));

        mvc.perform(get("/api/dashboard").header(HttpHeaders.AUTHORIZATION, reception))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeMembers").value(3))
                .andExpect(jsonPath("$.monthRevenue").doesNotExist());
    }

    @Test
    void sinTokenNoSeAccede() throws Exception {
        mvc.perform(post("/api/check-ins").contentType(MediaType.APPLICATION_JSON).content("{\"memberId\":1}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
    }
}
