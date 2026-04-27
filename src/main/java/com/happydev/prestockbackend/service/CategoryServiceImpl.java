package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.Category;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.CategoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
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
        return categoryRepository.save(category);
    }

    @Override
    public Category updateCategory(@NonNull Long id, @NonNull Category categoryDetails) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        category.setName(categoryDetails.getName());
        // Actualiza otros campos si es necesario.
        return categoryRepository.save(category);
    }

    @Override
    public void deleteCategory(@NonNull Long id) {
        categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
        categoryRepository.deleteById(id);
    }
}
