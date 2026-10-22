package com.gymflow.membership;

import java.util.List;

import com.gymflow.membership.MembershipDtos.ContractRequest;
import com.gymflow.membership.MembershipDtos.MembershipResponse;
import com.gymflow.membership.MembershipDtos.PaymentRequest;
import com.gymflow.security.CurrentUser;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class MembershipController {

    private final MembershipService service;

    MembershipController(MembershipService service) {
        this.service = service;
    }

    @GetMapping("/members/{memberId}/memberships")
    List<MembershipResponse> list(@PathVariable long memberId) {
        return service.listForMember(memberId);
    }

    @PostMapping("/members/{memberId}/memberships")
    @ResponseStatus(HttpStatus.CREATED)
    MembershipResponse contract(@PathVariable long memberId, @Valid @RequestBody ContractRequest req) {
        return service.contract(memberId, req, CurrentUser.id());
    }

    @PostMapping("/memberships/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    MembershipResponse pay(@PathVariable long id, @Valid @RequestBody PaymentRequest req) {
        return service.registerPayment(id, req, CurrentUser.id());
    }

    @PatchMapping("/memberships/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    MembershipResponse cancel(@PathVariable long id) {
        return service.cancel(id);
    }
}
