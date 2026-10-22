package com.gymflow.dashboard;

import com.gymflow.dashboard.DashboardService.Summary;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
class DashboardController {

    private final DashboardService service;

    DashboardController(DashboardService service) {
        this.service = service;
    }

    /** Los importes (ingresos y saldo pendiente) sólo se incluyen para ADMIN; recepción recibe null. */
    @GetMapping
    Summary get(Authentication auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return service.summary(admin);
    }
}
