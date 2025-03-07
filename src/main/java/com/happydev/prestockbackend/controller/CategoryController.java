package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.entity.Category;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.service.CategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@CrossOrigin(origins = "*") // O la URL de tu frontend en producción
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<List<Category>> getAllCategories() {
        List<Category> categories = categoryService.findAllCategories();
        return new ResponseEntity<>(categories, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Category> getCategoryById(@PathVariable Long id) {
        return categoryService.findCategoryById(id)
                .map(category -> new ResponseEntity<>(category, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<Category> createCategory(@RequestBody Category category) {
        Category savedCategory = categoryService.saveCategory(category);
        return new ResponseEntity<>(savedCategory, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Category> updateCategory(@PathVariable Long id, @RequestBody Category categoryDetails) {
        try{
            Category updatedCategory = categoryService.updateCategory(id, categoryDetails);
            return new ResponseEntity<>(updatedCategory, HttpStatus.OK);
        }catch (ResourceNotFoundException e){
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        try{
            categoryService.deleteCategory(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (ResourceNotFoundException e){
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}
