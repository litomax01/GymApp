package com.gymflow.checkin;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    @Query("select c from CheckIn c order by c.checkedInAt desc")
    List<CheckIn> findRecent(Pageable pageable);

    @Query("select count(c) from CheckIn c where c.result = 'ALLOWED' and c.checkedInAt >= :from and c.checkedInAt < :to")
    long countAllowedBetween(Instant from, Instant to);
}
