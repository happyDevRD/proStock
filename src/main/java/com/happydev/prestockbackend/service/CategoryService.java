package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryService {
    List<Category> findAllCategories();
    Optional<Category> findCategoryById(Long id);
    Category saveCategory(Category category);
    Category updateCategory(Long id, Category categoryDetails);
    void deleteCategory(Long id);
}
