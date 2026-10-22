package com.gymflow.membership;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.gymflow.common.BusinessRuleException;
import com.gymflow.common.NotFoundException;
import com.gymflow.member.Member;
import com.gymflow.member.MemberRepository;
import com.gymflow.membership.MembershipDtos.ContractRequest;
import com.gymflow.membership.MembershipDtos.MembershipResponse;
import com.gymflow.membership.MembershipDtos.PaymentRequest;
import com.gymflow.membership.MembershipDtos.PaymentResponse;
import com.gymflow.plan.Plan;
import com.gymflow.plan.PlanRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MembershipService {

    private static final int MAX_BACKDATE_DAYS = 31;

    private final MembershipRepository memberships;
    private final PaymentRepository payments;
    private final MemberRepository members;
    private final PlanRepository plans;
    private final Clock clock;

    MembershipService(MembershipRepository memberships, PaymentRepository payments, MemberRepository members,
                      PlanRepository plans, Clock clock) {
        this.memberships = memberships;
        this.payments = payments;
        this.members = members;
        this.plans = plans;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> listForMember(long memberId) {
        if (!members.existsById(memberId)) throw new NotFoundException("Socio", memberId);
        return toResponses(memberships.findByMember(memberId));
    }

    /**
     * Contrata o renueva un plan y, opcionalmente, registra el primer cobro, todo en una transacción.
     * Se bloquea la fila del socio para que dos contrataciones simultáneas no puedan solaparse.
     */
    public MembershipResponse contract(long memberId, ContractRequest req, long userId) {
        Member member = members.findForUpdate(memberId).orElseThrow(() -> new NotFoundException("Socio", memberId));
        if (!member.isActive()) {
            throw new BusinessRuleException("MEMBER_INACTIVE", "El socio está dado de baja");
        }
        Plan plan = plans.findById(req.planId()).orElseThrow(() -> new NotFoundException("Plan", req.planId()));
        if (!plan.isActive()) {
            throw new BusinessRuleException("PLAN_INACTIVE", "El plan no está disponible");
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate start = req.startDate() != null ? req.startDate() : defaultStart(memberId, today);
        if (start.isBefore(today.minusDays(MAX_BACKDATE_DAYS))) {
            throw new BusinessRuleException("START_TOO_OLD",
                    "La fecha de inicio no puede ser anterior a " + MAX_BACKDATE_DAYS + " días");
        }
        LocalDate end = start.plusDays(plan.getDurationDays() - 1L);
        if (memberships.overlaps(memberId, start, end)) {
            throw new BusinessRuleException("MEMBERSHIP_OVERLAP",
                    "El periodo se solapa con otra membresía del socio");
        }

        Membership m = new Membership();
        m.setMember(member);
        m.setPlan(plan);
        m.setStartDate(start);
        m.setEndDate(end);
        m.setPrice(plan.getPrice()); // precio congelado
        memberships.save(m);

        if (req.payment() != null) {
            addPayment(m, req.payment(), userId);
        }
        return toResponses(List.of(m)).get(0);
    }

    public MembershipResponse registerPayment(long membershipId, PaymentRequest req, long userId) {
        Membership m = memberships.findForUpdate(membershipId)
                .orElseThrow(() -> new NotFoundException("Membresía", membershipId));
        if (m.getStatus() == Membership.Status.CANCELED) {
            throw new BusinessRuleException("MEMBERSHIP_CANCELED", "La membresía está cancelada");
        }
        addPayment(m, req, userId);
        return toResponses(List.of(m)).get(0);
    }

    public MembershipResponse cancel(long membershipId) {
        Membership m = memberships.findForUpdate(membershipId)
                .orElseThrow(() -> new NotFoundException("Membresía", membershipId));
        m.setStatus(Membership.Status.CANCELED);
        return toResponses(List.of(m)).get(0);
    }

    private LocalDate defaultStart(long memberId, LocalDate today) {
        return memberships.latestEnd(memberId)
                .filter(end -> !end.isBefore(today))
                .map(end -> end.plusDays(1))
                .orElse(today);
    }

    private void addPayment(Membership m, PaymentRequest req, long userId) {
        BigDecimal paid = payments.findByMembershipIdInOrderByPaidAtAsc(List.of(m.getId())).stream()
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = m.getPrice().subtract(paid);
        if (req.amount().compareTo(balance) > 0) {
            throw new BusinessRuleException("OVERPAYMENT", "El importe supera el saldo pendiente (" + balance + ")");
        }
        Payment p = new Payment();
        p.setMembership(m);
        p.setAmount(req.amount());
        p.setMethod(req.method());
        p.setReference(req.reference() == null || req.reference().isBlank() ? null : req.reference().trim());
        p.setRegisteredBy(userId);
        payments.save(p);
    }

    private List<MembershipResponse> toResponses(Collection<Membership> list) {
        LocalDate today = LocalDate.now(clock);
        Map<Long, List<Payment>> byMembership = list.isEmpty() ? Map.of()
                : payments.findByMembershipIdInOrderByPaidAtAsc(list.stream().map(Membership::getId).toList())
                        .stream().collect(Collectors.groupingBy(p -> p.getMembership().getId()));
        return list.stream().map(m -> {
            List<Payment> ps = byMembership.getOrDefault(m.getId(), List.of());
            BigDecimal paid = ps.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new MembershipResponse(m.getId(), m.getMember().getId(), m.getPlan().getId(), m.getPlan().getName(),
                    m.getStartDate(), m.getEndDate(), m.getPrice(), paid, m.getPrice().subtract(paid),
                    m.stateOn(today).name(), ps.stream().map(PaymentResponse::of).toList());
        }).toList();
    }
}
