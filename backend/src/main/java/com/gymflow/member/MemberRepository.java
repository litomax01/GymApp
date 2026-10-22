package com.gymflow.member;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findForUpdate(@Param("id") long id);

    boolean existsByDocumentNumber(String documentNumber);

    boolean existsByDocumentNumberAndIdNot(String documentNumber, Long id);

    /**
     * Busca por nombre, apellido o documento (parcial, sin distinguir mayúsculas). q = "" desactiva el filtro:
     * se evita un parámetro null porque PostgreSQL lo tipa como bytea y rompe lower().
     */
    @Query("""
            select m from Member m
            where (:onlyActive = false or m.active = true)
              and (:q = ''
                   or lower(m.firstName) like lower(concat('%', :q, '%'))
                   or lower(m.lastName) like lower(concat('%', :q, '%'))
                   or lower(concat(m.firstName, ' ', m.lastName)) like lower(concat('%', :q, '%'))
                   or m.documentNumber like concat('%', :q, '%'))
            """)
    Page<Member> search(@Param("q") String q, @Param("onlyActive") boolean onlyActive, Pageable pageable);
}
