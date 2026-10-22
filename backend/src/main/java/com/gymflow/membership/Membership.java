package com.gymflow.membership;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.gymflow.member.Member;
import com.gymflow.plan.Plan;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "memberships")
@Getter
@Setter
@NoArgsConstructor
public class Membership {

    public enum Status { ACTIVE, CANCELED }

    /** Estado derivado de fechas y status; no se persiste para que nunca quede desfasado. */
    public enum State { CURRENT, UPCOMING, EXPIRED, CANCELED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public State stateOn(LocalDate today) {
        if (status == Status.CANCELED) return State.CANCELED;
        if (startDate.isAfter(today)) return State.UPCOMING;
        if (endDate.isBefore(today)) return State.EXPIRED;
        return State.CURRENT;
    }
}
