package com.gymflow.plan;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    List<Plan> findByActiveTrueOrderByDurationDaysAsc();

    List<Plan> findAllByOrderByDurationDaysAsc();

    @Query("select count(p) > 0 from Plan p where lower(p.name) = lower(:name) and p.id <> :excludeId")
    /** excludeId = -1 para no excluir ninguno (evita parámetros null, que PostgreSQL no puede tipar). */
    boolean nameTaken(String name, long excludeId);
}
