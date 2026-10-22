package com.gymflow.member;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class MemberDtos {

    private MemberDtos() {}

    public record MemberRequest(
            @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9.\\-]+", message = "solo letras, números, punto y guion") String documentNumber,
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @Email @Size(max = 160) String email,
            @Size(max = 30) @Pattern(regexp = "[0-9+() \\-]*", message = "teléfono inválido") String phone,
            @Past LocalDate birthDate,
            @Size(max = 500) String notes) {}

    public record MemberResponse(
            Long id, String documentNumber, String firstName, String lastName, String email, String phone,
            LocalDate birthDate, String notes, boolean active, Instant createdAt) {

        static MemberResponse of(Member m) {
            return new MemberResponse(m.getId(), m.getDocumentNumber(), m.getFirstName(), m.getLastName(),
                    m.getEmail(), m.getPhone(), m.getBirthDate(), m.getNotes(), m.isActive(), m.getCreatedAt());
        }
    }
}
