package com.gymflow.plan;

import java.math.BigDecimal;
import java.util.List;

import com.gymflow.common.BusinessRuleException;
import com.gymflow.common.NotFoundException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/plans")
@Transactional
class PlanController {

    private final PlanRepository repo;

    PlanController(PlanRepository repo) {
        this.repo = repo;
    }

    record PlanRequest(
            @NotBlank @Size(max = 80) String name,
            @NotNull @DecimalMin("0.00") @DecimalMax("99999.99") BigDecimal price,
            @Min(1) @Max(1830) int durationDays) {}

    record PlanResponse(Long id, String name, BigDecimal price, int durationDays, boolean active) {
        static PlanResponse of(Plan p) {
            return new PlanResponse(p.getId(), p.getName(), p.getPrice(), p.getDurationDays(), p.isActive());
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<PlanResponse> list(@RequestParam(defaultValue = "true") boolean onlyActive) {
        var plans = onlyActive ? repo.findByActiveTrueOrderByDurationDaysAsc() : repo.findAllByOrderByDurationDaysAsc();
        return plans.stream().map(PlanResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    PlanResponse create(@Valid @RequestBody PlanRequest req) {
        checkName(req.name(), null);
        return PlanResponse.of(repo.save(new Plan(req.name().trim(), req.price(), req.durationDays())));
    }

    /** El precio nuevo no afecta a membresías ya contratadas (guardan su propio precio). */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    PlanResponse update(@PathVariable long id, @Valid @RequestBody PlanRequest req) {
        Plan p = find(id);
        checkName(req.name(), id);
        p.setName(req.name().trim());
        p.setPrice(req.price());
        p.setDurationDays(req.durationDays());
        return PlanResponse.of(p);
    }

    @PatchMapping("/{id}/active/{value}")
    @PreAuthorize("hasRole('ADMIN')")
    PlanResponse setActive(@PathVariable long id, @PathVariable boolean value) {
        Plan p = find(id);
        p.setActive(value);
        return PlanResponse.of(p);
    }

    private Plan find(long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Plan", id));
    }

    private void checkName(String name, Long excludeId) {
        if (repo.nameTaken(name.trim(), excludeId == null ? -1L : excludeId)) {
            throw new BusinessRuleException("DUPLICATE_PLAN", "Ya existe un plan con el nombre " + name.trim());
        }
    }
}
