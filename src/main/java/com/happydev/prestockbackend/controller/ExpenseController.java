package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.ExpenseDto;
import com.happydev.prestockbackend.service.ExpenseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/expenses")
@Tag(name = "Expenses", description = "Gastos directos (sin orden de compra)")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('view.expenses')")
    public Page<ExpenseDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String description,
            @RequestParam(required = false) String category) {
        return expenseService.findAll(description, category,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "expenseDate")));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('view.expenses')")
    public ResponseEntity<ExpenseDto> getById(@PathVariable Long id) {
        return expenseService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('expenses.create')")
    public ResponseEntity<ExpenseDto> create(@RequestBody ExpenseDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(expenseService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('expenses.edit')")
    public ResponseEntity<ExpenseDto> update(@PathVariable Long id, @RequestBody ExpenseDto dto) {
        return ResponseEntity.ok(expenseService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('expenses.delete')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        expenseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
