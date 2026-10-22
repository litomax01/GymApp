package com.gymflow.membership;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class MembershipDtos {

    private MembershipDtos() {}

    public record PaymentRequest(
            @NotNull @DecimalMin(value = "0.01", message = "debe ser mayor que cero") @DecimalMax("99999.99") BigDecimal amount,
            @NotNull Payment.Method method,
            @Size(max = 80) String reference) {}

    /** startDate opcional: si falta, la membresía empieza hoy o al terminar la vigente (renovación encadenada). */
    public record ContractRequest(@NotNull Long planId, LocalDate startDate, @Valid PaymentRequest payment) {}

    public record PaymentResponse(Long id, BigDecimal amount, String method, String reference, Instant paidAt) {
        static PaymentResponse of(Payment p) {
            return new PaymentResponse(p.getId(), p.getAmount(), p.getMethod().name(), p.getReference(), p.getPaidAt());
        }
    }

    public record MembershipResponse(
            Long id, Long memberId, Long planId, String planName, LocalDate startDate, LocalDate endDate,
            BigDecimal price, BigDecimal paid, BigDecimal balance, String state, List<PaymentResponse> payments) {}
}
