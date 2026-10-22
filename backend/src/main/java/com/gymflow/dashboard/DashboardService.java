package com.gymflow.dashboard;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consultas de lectura agregadas; SQL directo porque son informes, no operaciones sobre el modelo. */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    static final int RISK_INACTIVE_DAYS = 14;
    static final int EXPIRING_DAYS = 7;

    public record AtRiskMember(Long memberId, String name, String phone, String reason, LocalDate lastVisit,
                               LocalDate membershipEnd) {}

    public record Summary(long activeMembers, long expiringSoon, long checkInsToday, BigDecimal monthRevenue,
                          BigDecimal pendingBalance, List<AtRiskMember> atRisk) {}

    private final JdbcClient jdbc;
    private final Clock clock;

    DashboardService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Summary summary(boolean includeMoney) {
        LocalDate today = LocalDate.now(clock);
        var zone = clock.getZone();
        Instant dayStart = today.atStartOfDay(zone).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
        YearMonth ym = YearMonth.from(today);
        Instant monthStart = ym.atDay(1).atStartOfDay(zone).toInstant();
        Instant monthEnd = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();

        long active = jdbc.sql("""
                select count(distinct m.member_id) from memberships m join members b on b.id = m.member_id
                where b.active and m.status = 'ACTIVE' and m.start_date <= :today and m.end_date >= :today
                """).param("today", today).query(Long.class).single();

        long expiring = jdbc.sql("""
                select count(distinct m.member_id) from memberships m join members b on b.id = m.member_id
                where b.active and m.status = 'ACTIVE' and m.start_date <= :today
                  and m.end_date between :today and :limit
                  and not exists (select 1 from memberships n where n.member_id = m.member_id and n.status = 'ACTIVE'
                                  and n.start_date > m.end_date)
                """).param("today", today).param("limit", today.plusDays(EXPIRING_DAYS)).query(Long.class).single();

        long checkIns = jdbc.sql("select count(*) from check_ins where result = 'ALLOWED' and checked_in_at >= :a and checked_in_at < :b")
                .param("a", java.sql.Timestamp.from(dayStart)).param("b", java.sql.Timestamp.from(dayEnd))
                .query(Long.class).single();

        BigDecimal revenue = null;
        BigDecimal pending = null;
        if (includeMoney) {
            revenue = jdbc.sql("select coalesce(sum(amount), 0) from payments where paid_at >= :a and paid_at < :b")
                    .param("a", java.sql.Timestamp.from(monthStart)).param("b", java.sql.Timestamp.from(monthEnd))
                    .query(BigDecimal.class).single();
            pending = jdbc.sql("""
                    select coalesce(sum(m.price - coalesce((select sum(p.amount) from payments p where p.membership_id = m.id), 0)), 0)
                    from memberships m where m.status = 'ACTIVE'
                    """).query(BigDecimal.class).single();
        }
        return new Summary(active, expiring, checkIns, revenue, pending, atRisk(today));
    }

    /**
     * Radar de retención: socios con membresía vigente que (a) llevan >= 14 días sin visita (o nunca vinieron y
     * la membresía empezó hace >= 14 días) o (b) vencen en <= 7 días sin renovación. Los primeros primero.
     */
    List<AtRiskMember> atRisk(LocalDate today) {
        var zone = clock.getZone();
        return jdbc.sql("""
                select b.id, b.first_name || ' ' || b.last_name as name, b.phone,
                       m.end_date, m.start_date, v.last_visit
                from members b
                join memberships m on m.member_id = b.id and m.status = 'ACTIVE'
                     and m.start_date <= :today and m.end_date >= :today
                left join (select member_id, max(checked_in_at) as last_visit from check_ins
                           where result = 'ALLOWED' group by member_id) v on v.member_id = b.id
                where b.active
                order by b.last_name, b.first_name
                """)
                .param("today", today)
                .query((rs, n) -> {
                    var lastTs = rs.getTimestamp("last_visit");
                    LocalDate last = lastTs == null ? null : lastTs.toInstant().atZone(zone).toLocalDate();
                    LocalDate end = rs.getDate("end_date").toLocalDate();
                    LocalDate start = rs.getDate("start_date").toLocalDate();
                    return classify(rs.getLong("id"), rs.getString("name"), rs.getString("phone"), last, start, end, today);
                })
                .list().stream().filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing((AtRiskMember a) -> !a.reason().equals("INACTIVE")))
                .limit(50).toList();
    }

    static AtRiskMember classify(long id, String name, String phone, LocalDate lastVisit, LocalDate start,
                                 LocalDate end, LocalDate today) {
        LocalDate reference = lastVisit != null ? lastVisit : start;
        if (!reference.plusDays(RISK_INACTIVE_DAYS).isAfter(today)) {
            return new AtRiskMember(id, name, phone, "INACTIVE", lastVisit, end);
        }
        if (!end.isAfter(today.plusDays(EXPIRING_DAYS))) {
            return new AtRiskMember(id, name, phone, "EXPIRING", lastVisit, end);
        }
        return null;
    }
}
