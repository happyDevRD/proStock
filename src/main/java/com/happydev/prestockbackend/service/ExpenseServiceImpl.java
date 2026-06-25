package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.ExpenseDto;
import com.happydev.prestockbackend.entity.Expense;
import com.happydev.prestockbackend.entity.ExpenseCategory;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.ExpenseRepository;
import com.happydev.prestockbackend.repository.SupplierRepository;
import com.happydev.prestockbackend.util.SecurityAuditUtils;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final SupplierRepository supplierRepository;

    public ExpenseServiceImpl(ExpenseRepository expenseRepository,
                              SupplierRepository supplierRepository) {
        this.expenseRepository = expenseRepository;
        this.supplierRepository = supplierRepository;
    }

    @Override
    public Page<ExpenseDto> findAll(@NonNull String description, String category, @NonNull Pageable pageable) {
        String descFilter = description.isBlank() ? null : description;
        String catFilter = category == null || category.isBlank() ? null : category;
        return expenseRepository.findFiltered(descFilter, catFilter, pageable).map(this::toDto);
    }

    @Override
    public Optional<ExpenseDto> findById(@NonNull Long id) {
        return expenseRepository.findById(id).map(this::toDto);
    }

    @Override
    public ExpenseDto create(@NonNull ExpenseDto dto) {
        Expense expense = fromDto(new Expense(), dto);
        expense.setCreatedBy(SecurityAuditUtils.currentUsernameOrNull());
        return toDto(expenseRepository.save(expense));
    }

    @Override
    public ExpenseDto update(@NonNull Long id, @NonNull ExpenseDto dto) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense", "id", id));
        return toDto(expenseRepository.save(fromDto(expense, dto)));
    }

    @Override
    public void delete(@NonNull Long id) {
        if (!expenseRepository.existsById(id)) {
            throw new ResourceNotFoundException("Expense", "id", id);
        }
        expenseRepository.deleteById(id);
    }

    @Override
    public List<Expense> findWithNcfInRange(@NonNull LocalDate from, @NonNull LocalDate to) {
        return expenseRepository.findWithNcfInRange(from, to);
    }

    private Expense fromDto(Expense expense, ExpenseDto dto) {
        expense.setDescription(dto.getDescription());
        expense.setAmount(dto.getAmount());
        expense.setItbis(dto.getItbis() != null ? dto.getItbis() : java.math.BigDecimal.ZERO);
        expense.setExpenseDate(dto.getExpenseDate());
        expense.setPaymentDate(dto.getPaymentDate());
        expense.setPaymentMethod(dto.getPaymentMethod());
        expense.setCategory(dto.getCategory() != null ? dto.getCategory() : ExpenseCategory.OTHER);
        expense.setNcfProveedor(dto.getNcfProveedor());
        expense.setTipoBienesServicios(
                dto.getTipoBienesServicios() != null ? dto.getTipoBienesServicios() : "09");
        expense.setRncCedula(dto.getRncCedula());
        expense.setTipoIdentificacion(dto.getTipoIdentificacion());
        expense.setNotes(dto.getNotes());
        if (dto.getSupplierId() != null) {
            expense.setSupplier(supplierRepository.findById(dto.getSupplierId()).orElse(null));
        } else {
            expense.setSupplier(null);
        }
        return expense;
    }

    private ExpenseDto toDto(Expense e) {
        ExpenseDto dto = new ExpenseDto();
        dto.setId(e.getId());
        dto.setDescription(e.getDescription());
        dto.setAmount(e.getAmount());
        dto.setItbis(e.getItbis());
        dto.setExpenseDate(e.getExpenseDate());
        dto.setPaymentDate(e.getPaymentDate());
        dto.setPaymentMethod(e.getPaymentMethod());
        dto.setCategory(e.getCategory());
        if (e.getSupplier() != null) {
            dto.setSupplierId(e.getSupplier().getId());
            dto.setSupplierName(e.getSupplier().getName());
        }
        dto.setNcfProveedor(e.getNcfProveedor());
        dto.setTipoBienesServicios(e.getTipoBienesServicios());
        dto.setRncCedula(e.getRncCedula());
        dto.setTipoIdentificacion(e.getTipoIdentificacion());
        dto.setNotes(e.getNotes());
        dto.setCreatedBy(e.getCreatedBy());
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }
}
