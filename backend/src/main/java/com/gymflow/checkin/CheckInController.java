package com.gymflow.checkin;

import java.util.List;

import com.gymflow.checkin.CheckInService.CheckInResponse;
import com.gymflow.security.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/check-ins")
class CheckInController {

    private final CheckInService service;

    CheckInController(CheckInService service) {
        this.service = service;
    }

    record CheckInRequest(@NotNull Long memberId) {}

    /** Siempre 200: un acceso denegado es un resultado de negocio válido, no un error HTTP. */
    @PostMapping
    CheckInResponse register(@Valid @RequestBody CheckInRequest req) {
        return service.register(req.memberId(), CurrentUser.id());
    }

    @GetMapping("/recent")
    List<CheckInResponse> recent(@RequestParam(defaultValue = "20") int limit) {
        return service.recent(limit);
    }
}
