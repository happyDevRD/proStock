package com.happydev.prestockbackend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.happydev.prestockbackend.security.Authz;

import com.happydev.prestockbackend.entity.Category;
import com.happydev.prestockbackend.service.CategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PreAuthorize(Authz.CATEGORY_READ)
    @GetMapping
    public ResponseEntity<List<Category>> getAllCategories() {
        List<Category> categories = categoryService.findAllCategories();
        return new ResponseEntity<>(categories, HttpStatus.OK);
    }

    @PreAuthorize(Authz.CATEGORY_READ)
    @GetMapping("/{id}")
    public ResponseEntity<Category> getCategoryById(@PathVariable @NonNull Long id) {
        return categoryService.findCategoryById(id)
                .map(category -> new ResponseEntity<>(category, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PreAuthorize(Authz.CATEGORY_CREATE)
    @PostMapping
    public ResponseEntity<Category> createCategory(@RequestBody @NonNull Category category) {
        Category savedCategory = categoryService.saveCategory(category);
        return new ResponseEntity<>(savedCategory, HttpStatus.CREATED);
    }

    @PreAuthorize(Authz.PRODUCT_EDIT)
    @PutMapping("/{id}")
    public ResponseEntity<Category> updateCategory(@PathVariable @NonNull Long id, @RequestBody @NonNull Category categoryDetails) {
        Category updatedCategory = categoryService.updateCategory(id, categoryDetails);
        return new ResponseEntity<>(updatedCategory, HttpStatus.OK);
    }

    @PreAuthorize(Authz.PRODUCT_EDIT)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable @NonNull Long id) {
        categoryService.deleteCategory(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
