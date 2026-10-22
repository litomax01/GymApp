package com.gymflow.checkin;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.gymflow.checkin.CheckIn.Result;
import com.gymflow.common.NotFoundException;
import com.gymflow.member.Member;
import com.gymflow.member.MemberRepository;
import com.gymflow.membership.Membership;
import com.gymflow.membership.MembershipRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CheckInService {

    public record CheckInResponse(Long id, Long memberId, String memberName, String result, String reason,
                                  Instant checkedInAt, LocalDate membershipEnd, Long daysLeft) {}

    private final CheckInRepository checkIns;
    private final MemberRepository members;
    private final MembershipRepository memberships;
    private final Clock clock;

    CheckInService(CheckInRepository checkIns, MemberRepository members, MembershipRepository memberships,
                   Clock clock) {
        this.checkIns = checkIns;
        this.members = members;
        this.memberships = memberships;
        this.clock = clock;
    }

    /** Decide y registra el acceso. Los intentos denegados también se guardan (auditoría y estadísticas). */
    public CheckInResponse register(long memberId, long userId) {
        Member member = members.findById(memberId).orElseThrow(() -> new NotFoundException("Socio", memberId));
        LocalDate today = LocalDate.now(clock);

        Result result;
        String reason = null;
        Membership current = null;
        if (!member.isActive()) {
            result = Result.DENIED;
            reason = "MEMBER_INACTIVE";
        } else {
            current = memberships.findCurrent(memberId, today).orElse(null);
            if (current != null) {
                result = Result.ALLOWED;
            } else {
                result = Result.DENIED;
                reason = memberships.latestEnd(memberId).isPresent() ? "MEMBERSHIP_EXPIRED" : "NO_MEMBERSHIP";
            }
        }

        CheckIn c = new CheckIn();
        c.setMemberId(memberId);
        c.setCheckedInAt(Instant.now(clock));
        c.setResult(result);
        c.setReason(reason);
        c.setRegisteredBy(userId);
        checkIns.save(c);

        LocalDate end = current != null ? current.getEndDate() : null;
        Long daysLeft = end != null ? ChronoUnit.DAYS.between(today, end) : null;
        return new CheckInResponse(c.getId(), memberId, fullName(member), result.name(), reason, c.getCheckedInAt(),
                end, daysLeft);
    }

    @Transactional(readOnly = true)
    public List<CheckInResponse> recent(int limit) {
        List<CheckIn> list = checkIns.findRecent(PageRequest.of(0, Math.max(1, Math.min(limit, 100))));
        Map<Long, Member> byId = members.findAllById(list.stream().map(CheckIn::getMemberId).distinct().toList())
                .stream().collect(Collectors.toMap(Member::getId, Function.identity()));
        return list.stream()
                .map(c -> new CheckInResponse(c.getId(), c.getMemberId(), fullName(byId.get(c.getMemberId())),
                        c.getResult().name(), c.getReason(), c.getCheckedInAt(), null, null))
                .toList();
    }

    private static String fullName(Member m) {
        return m.getFirstName() + " " + m.getLastName();
    }
}
