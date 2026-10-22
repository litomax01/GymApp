package com.gymflow.member;

import com.gymflow.common.BusinessRuleException;
import com.gymflow.common.NotFoundException;
import com.gymflow.member.MemberDtos.MemberRequest;
import com.gymflow.member.MemberDtos.MemberResponse;

import com.gymflow.common.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MemberService {

    private final MemberRepository repo;

    MemberService(MemberRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public PageResponse<MemberResponse> search(String q, boolean onlyActive, Pageable pageable) {
        String term = (q == null) ? "" : q.trim();
        return PageResponse.of(repo.search(term, onlyActive, pageable), MemberResponse::of);
    }

    @Transactional(readOnly = true)
    public MemberResponse get(long id) {
        return MemberResponse.of(find(id));
    }

    public MemberResponse create(MemberRequest req) {
        String doc = req.documentNumber().trim();
        if (repo.existsByDocumentNumber(doc)) {
            throw duplicated(doc);
        }
        Member m = new Member();
        apply(m, req);
        return MemberResponse.of(repo.save(m));
    }

    public MemberResponse update(long id, MemberRequest req) {
        Member m = find(id);
        String doc = req.documentNumber().trim();
        if (repo.existsByDocumentNumberAndIdNot(doc, id)) {
            throw duplicated(doc);
        }
        apply(m, req);
        return MemberResponse.of(m);
    }

    /** Baja lógica: se conserva el historial de pagos y asistencias. */
    public MemberResponse setActive(long id, boolean active) {
        Member m = find(id);
        m.setActive(active);
        return MemberResponse.of(m);
    }

    public Member find(long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Socio", id));
    }

    private static void apply(Member m, MemberRequest r) {
        m.setDocumentNumber(r.documentNumber().trim());
        m.setFirstName(r.firstName().trim());
        m.setLastName(r.lastName().trim());
        m.setEmail(blankToNull(r.email()));
        m.setPhone(blankToNull(r.phone()));
        m.setBirthDate(r.birthDate());
        m.setNotes(blankToNull(r.notes()));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static BusinessRuleException duplicated(String doc) {
        return new BusinessRuleException("DUPLICATE_DOCUMENT", "Ya existe un socio con el documento " + doc);
    }
}
