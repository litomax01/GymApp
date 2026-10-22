package com.gymflow.checkin;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "check_ins")
@Getter
@Setter
@NoArgsConstructor
public class CheckIn {

    public enum Result { ALLOWED, DENIED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "checked_in_at", nullable = false)
    private Instant checkedInAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Result result;

    private String reason;

    @Column(name = "registered_by", nullable = false)
    private Long registeredBy;
}
