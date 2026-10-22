package com.gymflow.membership;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    @Query("select m from Membership m join fetch m.plan where m.member.id = :memberId order by m.startDate desc")
    List<Membership> findByMember(long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Membership m join fetch m.plan where m.id = :id")
    Optional<Membership> findForUpdate(long id);

    @Query("""
            select count(m) > 0 from Membership m
            where m.member.id = :memberId and m.status = 'ACTIVE'
              and m.startDate <= :end and m.endDate >= :start
            """)
    boolean overlaps(long memberId, LocalDate start, LocalDate end);

    @Query("select max(m.endDate) from Membership m where m.member.id = :memberId and m.status = 'ACTIVE'")
    Optional<LocalDate> latestEnd(long memberId);

    @Query("""
            select m from Membership m join fetch m.plan
            where m.member.id = :memberId and m.status = 'ACTIVE'
              and m.startDate <= :today and m.endDate >= :today
            """)
    Optional<Membership> findCurrent(long memberId, LocalDate today);
}
