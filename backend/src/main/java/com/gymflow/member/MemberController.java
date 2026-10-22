package com.gymflow.member;

import com.gymflow.member.MemberDtos.MemberRequest;
import com.gymflow.member.MemberDtos.MemberResponse;

import jakarta.validation.Valid;

import com.gymflow.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
class MemberController {

    private final MemberService service;

    MemberController(MemberService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<MemberResponse> list(@RequestParam(required = false) String q,
                              @RequestParam(defaultValue = "true") boolean onlyActive,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        var pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by("lastName", "firstName"));
        return service.search(q, onlyActive, pageable);
    }

    @GetMapping("/{id}")
    MemberResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    MemberResponse create(@Valid @RequestBody MemberRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    MemberResponse update(@PathVariable long id, @Valid @RequestBody MemberRequest req) {
        return service.update(id, req);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    MemberResponse deactivate(@PathVariable long id) {
        return service.setActive(id, false);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    MemberResponse activate(@PathVariable long id) {
        return service.setActive(id, true);
    }
}
