package com.gymflow.membership;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class MembershipApiTest extends PostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestAuth auth;
    @Autowired TestDb db;

    String admin;
    String reception;
    int planId;
    int memberId;

    @BeforeEach
    void setUp() throws Exception {
        db.reset();
        admin = auth.bearer("admin@gymflow.test", Role.ADMIN);
        reception = auth.bearer("recepcion@gymflow.test", Role.RECEPTION);
        planId = id(send(post("/api/plans"), admin, "{\"name\":\"Mensual\",\"price\":30.00,\"durationDays\":30}")
                .andExpect(status().isCreated()));
        memberId = id(send(post("/api/members"), reception,
                "{\"documentNumber\":\"D1\",\"firstName\":\"Ana\",\"lastName\":\"Prueba\"}")
                .andExpect(status().isCreated()));
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                               String token, String json) throws Exception {
        return mvc.perform(req.header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static int id(ResultActions r) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions contract(String body) throws Exception {
        return send(post("/api/members/" + memberId + "/memberships"), reception, body);
    }

    @Test
    void contratarCalculaVigenciaCongelaPrecioYRegistraPagoEnUnaTransaccion() throws Exception {
        LocalDate today = LocalDate.now();
        contract("{\"planId\":%d,\"payment\":{\"amount\":30.00,\"method\":\"CASH\"}}".formatted(planId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startDate").value(today.toString()))
                .andExpect(jsonPath("$.endDate").value(today.plusDays(29).toString()))
                .andExpect(jsonPath("$.state").value("CURRENT"))
                .andExpect(jsonPath("$.balance").value(0.0))
                .andExpect(jsonPath("$.payments.length()").value(1));

        // subir el precio del plan no cambia membresías ya contratadas
        send(put("/api/plans/" + planId), admin, "{\"name\":\"Mensual\",\"price\":99.00,\"durationDays\":30}")
                .andExpect(status().isOk());
        mvc.perform(get("/api/members/" + memberId + "/memberships").header(HttpHeaders.AUTHORIZATION, reception))
                .andExpect(jsonPath("$[0].price").value(30.0));
    }

    @Test
    void renovarEncadenaDespuesDeLaVigenteSinSolapar() throws Exception {
        contract("{\"planId\":%d}".formatted(planId)).andExpect(status().isCreated());
        LocalDate expectedStart = LocalDate.now().plusDays(30);
        contract("{\"planId\":%d}".formatted(planId)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.startDate").value(expectedStart.toString()))
                .andExpect(jsonPath("$.state").value("UPCOMING"));
    }

    @Test
    void fechaExplicitaQueSolapaDa409() throws Exception {
        contract("{\"planId\":%d}".formatted(planId)).andExpect(status().isCreated());
        contract("{\"planId\":%d,\"startDate\":\"%s\"}".formatted(planId, LocalDate.now().plusDays(10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_OVERLAP"));
    }

    @Test
    void dosContratacionesSimultaneasNoPuedenSolaparse() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        String body = "{\"planId\":%d,\"startDate\":\"%s\"}".formatted(planId, LocalDate.now());
        Callable<Integer> call = () -> contract(body).andReturn().getResponse().getStatus();
        Future<Integer> a = pool.submit(call);
        Future<Integer> b = pool.submit(call);
        var codes = java.util.List.of(a.get(), b.get());
        pool.shutdown();
        org.junit.jupiter.api.Assertions.assertEquals(1, codes.stream().filter(c -> c == 201).count(), codes.toString());
        org.junit.jupiter.api.Assertions.assertEquals(1, codes.stream().filter(c -> c == 409).count(), codes.toString());
    }

    @Test
    void pagoParcialDejaSaldoYSobrepagoSeRechaza() throws Exception {
        int msId = id(contract("{\"planId\":%d,\"payment\":{\"amount\":10.00,\"method\":\"CARD\"}}".formatted(planId))
                .andExpect(jsonPath("$.balance").value(20.0)));
        send(post("/api/memberships/" + msId + "/payments"), reception, "{\"amount\":25.00,\"method\":\"CASH\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OVERPAYMENT"));
        send(post("/api/memberships/" + msId + "/payments"), reception, "{\"amount\":20.00,\"method\":\"TRANSFER\",\"reference\":\"T-1\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.balance").value(0.0))
                .andExpect(jsonPath("$.payments.length()").value(2));
    }

    @Test
    void pagoNoPositivoSeRechazaConValidacion() throws Exception {
        int msId = id(contract("{\"planId\":%d}".formatted(planId)));
        send(post("/api/memberships/" + msId + "/payments"), reception, "{\"amount\":0,\"method\":\"CASH\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields[0].field").value("amount"));
    }

    @Test
    void contratacionConPagoInvalidoNoDejaMembresiaHuerfana() throws Exception {
        // el pago supera el precio: la transacción completa debe revertirse
        contract("{\"planId\":%d,\"payment\":{\"amount\":500.00,\"method\":\"CASH\"}}".formatted(planId))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/members/" + memberId + "/memberships").header(HttpHeaders.AUTHORIZATION, reception))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void soloAdminCancelaMembresiasYGestionaPlanes() throws Exception {
        int msId = id(contract("{\"planId\":%d}".formatted(planId)));
        mvc.perform(patch("/api/memberships/" + msId + "/cancel").header(HttpHeaders.AUTHORIZATION, reception))
                .andExpect(status().isForbidden());
        send(post("/api/plans"), reception, "{\"name\":\"X\",\"price\":1,\"durationDays\":1}").andExpect(status().isForbidden());
        mvc.perform(patch("/api/memberships/" + msId + "/cancel").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CANCELED"));
        // cancelada: ya no admite cobros y permite contratar de nuevo
        send(post("/api/memberships/" + msId + "/payments"), reception, "{\"amount\":5,\"method\":\"CASH\"}")
                .andExpect(status().isConflict());
        contract("{\"planId\":%d}".formatted(planId)).andExpect(status().isCreated());
    }

    @Test
    void noSeContrataConSocioDeBajaNiPlanInactivo() throws Exception {
        send(patch("/api/plans/" + planId + "/active/false"), admin, "").andExpect(status().isOk());
        contract("{\"planId\":%d}".formatted(planId)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_INACTIVE"));
        send(patch("/api/plans/" + planId + "/active/true"), admin, "");
        mvc.perform(patch("/api/members/" + memberId + "/deactivate").header(HttpHeaders.AUTHORIZATION, admin));
        contract("{\"planId\":%d}".formatted(planId)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_INACTIVE"));
    }
}
