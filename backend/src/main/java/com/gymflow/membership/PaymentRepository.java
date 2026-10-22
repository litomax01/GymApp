package com.gymflow.membership;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByMembershipIdInOrderByPaidAtAsc(Collection<Long> membershipIds);
}
