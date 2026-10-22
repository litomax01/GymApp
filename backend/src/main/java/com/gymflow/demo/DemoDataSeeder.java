package com.gymflow.demo;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Random;

import com.gymflow.checkin.CheckIn;
import com.gymflow.checkin.CheckInRepository;
import com.gymflow.member.Member;
import com.gymflow.member.MemberRepository;
import com.gymflow.membership.Membership;
import com.gymflow.membership.MembershipRepository;
import com.gymflow.membership.Payment;
import com.gymflow.membership.PaymentRepository;
import com.gymflow.plan.Plan;
import com.gymflow.plan.PlanRepository;
import com.gymflow.user.AppUserRepository;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Datos ficticios para demostración (perfil "demo"). Genera una cartera con perfiles variados:
 * al día, por vencer, vencidos, inactivos (radar de retención) y con saldo pendiente.
 * Semilla fija: siempre produce los mismos socios. Sólo se ejecuta si no hay socios.
 */
@Component
@Profile("demo")
@Order(2)
class DemoDataSeeder implements ApplicationRunner {

    private static final String[] FIRST = {"Lucía", "Mateo", "Sofía", "Hugo", "Valeria", "Martín", "Emma", "Leo",
            "Paula", "Diego", "Carla", "Álvaro", "Irene", "Pablo", "Noa", "Adrián", "Julia", "Marcos", "Lola", "Iván",
            "Clara", "Sergio", "Marta", "Bruno"};
    private static final String[] LAST = {"García", "Martínez", "López", "Sánchez", "Pérez", "Gómez", "Ruiz",
            "Hernández", "Díaz", "Moreno", "Muñoz", "Álvarez", "Romero", "Navarro", "Torres", "Domínguez", "Gil",
            "Vázquez", "Serrano", "Blanco", "Molina", "Castro", "Ortega", "Rubio"};

    private final MemberRepository members;
    private final PlanRepository plans;
    private final MembershipRepository memberships;
    private final PaymentRepository payments;
    private final CheckInRepository checkIns;
    private final AppUserRepository users;
    private final Clock clock;

    DemoDataSeeder(MemberRepository members, PlanRepository plans, MembershipRepository memberships,
                   PaymentRepository payments, CheckInRepository checkIns, AppUserRepository users, Clock clock) {
        this.members = members;
        this.plans = plans;
        this.memberships = memberships;
        this.payments = payments;
        this.checkIns = checkIns;
        this.users = users;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (members.count() > 0) return;

        long userId = users.findByEmailIgnoreCase("recepcion@gymflow.test").orElseThrow().getId();
        Plan monthly = plans.save(new Plan("Mensual", new BigDecimal("30.00"), 30));
        Plan quarterly = plans.save(new Plan("Trimestral", new BigDecimal("80.00"), 90));
        Plan annual = plans.save(new Plan("Anual", new BigDecimal("280.00"), 365));
        List<Plan> catalog = List.of(monthly, quarterly, annual);

        LocalDate today = LocalDate.now(clock);
        Random rnd = new Random(42);

        for (int i = 0; i < FIRST.length; i++) {
            Member m = new Member();
            m.setDocumentNumber(String.valueOf(30_000_000 + i * 7919));
            m.setFirstName(FIRST[i]);
            m.setLastName(LAST[(i * 5) % LAST.length]);
            m.setEmail(FIRST[i].toLowerCase().replace("á", "a").replace("ú", "u").replace("í", "i") + i + "@ejemplo.test");
            m.setPhone("+34 6" + (10_000_000 + rnd.nextInt(89_999_999)));
            members.save(m);

            Plan plan = catalog.get(i % 3 == 2 ? 2 : (i % 2));
            // perfil: 0-11 al día, 12-15 por vencer, 16-19 vencidos, 20-23 vigentes pero inactivos
            int profile = i / 4;
            int daysLeft = switch (profile) {
                case 0, 1, 2 -> 10 + rnd.nextInt(plan.getDurationDays() - 10);
                case 3 -> 1 + rnd.nextInt(6);
                case 4 -> -(3 + rnd.nextInt(40));
                default -> 12 + rnd.nextInt(15);
            };
            LocalDate end = today.plusDays(daysLeft);
            Membership ms = new Membership();
            ms.setMember(m);
            ms.setPlan(plan);
            ms.setEndDate(end);
            ms.setStartDate(end.minusDays(plan.getDurationDays() - 1L));
            ms.setPrice(plan.getPrice());
            memberships.save(ms);

            boolean unpaid = i % 9 == 4; // algunos con saldo pendiente
            BigDecimal amount = unpaid ? plan.getPrice().divide(new BigDecimal("2")) : plan.getPrice();
            Payment p = new Payment();
            p.setMembership(ms);
            p.setAmount(amount);
            p.setMethod(Payment.Method.values()[i % 3]);
            p.setRegisteredBy(userId);
            p.setPaidAt(ms.getStartDate().atTime(LocalTime.NOON).atZone(clock.getZone()).toInstant()
                    .isAfter(java.time.Instant.now(clock)) ? java.time.Instant.now(clock)
                    : ms.getStartDate().atTime(LocalTime.NOON).atZone(clock.getZone()).toInstant());
            payments.save(p);

            seedVisits(m, profile, today, rnd, userId);
        }
    }

    private void seedVisits(Member m, int profile, LocalDate today, Random rnd, long userId) {
        int lastVisitAgo = profile == 5 ? 15 + rnd.nextInt(20) : rnd.nextInt(5);
        if (profile == 4) lastVisitAgo = 20 + rnd.nextInt(20);
        for (int back = lastVisitAgo; back < lastVisitAgo + 28; back += 1 + rnd.nextInt(3)) {
            CheckIn c = new CheckIn();
            c.setMemberId(m.getId());
            c.setResult(CheckIn.Result.ALLOWED);
            c.setRegisteredBy(userId);
            c.setCheckedInAt(today.minusDays(back).atTime(7 + rnd.nextInt(14), rnd.nextInt(60))
                    .atZone(clock.getZone()).toInstant());
            if (c.getCheckedInAt().isAfter(java.time.Instant.now(clock))) {
                c.setCheckedInAt(java.time.Instant.now(clock));
            }
            checkIns.save(c);
        }
    }
}
