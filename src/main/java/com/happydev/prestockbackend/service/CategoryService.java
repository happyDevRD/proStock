package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.Category;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;

public interface CategoryService {
    List<Category> findAllCategories();
    Optional<Category> findCategoryById(@NonNull Long id);
    Category saveCategory(@NonNull Category category);
    Category updateCategory(@NonNull Long id, @NonNull Category categoryDetails);
    void deleteCategory(@NonNull Long id);
}
