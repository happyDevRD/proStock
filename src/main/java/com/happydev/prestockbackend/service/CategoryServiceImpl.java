package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.Category;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.CategoryRepository;
import com.happydev.prestockbackend.util.SecurityAuditUtils;
import jakarta.transaction.Transactional;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    private final AuditService auditService;

    public CategoryServiceImpl(CategoryRepository categoryRepository, AuditService auditService) {
        this.categoryRepository = categoryRepository;
        this.auditService = auditService;
    }

    @Override
    public List<Category> findAllCategories() {
        return categoryRepository.findAll();
    }

    @Override
    public Optional<Category> findCategoryById(@NonNull Long id) {
        return categoryRepository.findById(id);
    }

    @Override
    public Category saveCategory(@NonNull Category category) {
        Category saved = categoryRepository.save(category);
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "CATEGORY_CREATED",
                "Category",
                saved.getId(),
                Map.of("name", saved.getName() != null ? saved.getName() : "")
        );
        return saved;
    }

    @Override
    public Category updateCategory(@NonNull Long id, @NonNull Category categoryDetails) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        category.setName(categoryDetails.getName());
        // Actualiza otros campos si es necesario.
        Category saved = categoryRepository.save(category);
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "CATEGORY_UPDATED",
                "Category",
                id,
                Map.of("name", saved.getName() != null ? saved.getName() : "")
        );
        return saved;
    }

    @Override
    public void deleteCategory(@NonNull Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
        String name = category.getName() != null ? category.getName() : "";
        categoryRepository.deleteById(id);
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "CATEGORY_DELETED",
                "Category",
                id,
                Map.of("name", name)
        );
    }
}
