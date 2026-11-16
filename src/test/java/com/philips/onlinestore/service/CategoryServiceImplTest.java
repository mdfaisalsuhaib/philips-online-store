package com.philips.onlinestore.service;

import com.philips.onlinestore.dto.CategoryRequest;
import com.philips.onlinestore.dto.CategoryResponse;
import com.philips.onlinestore.entity.Category;
import com.philips.onlinestore.repository.CategoryRepository;
import com.philips.onlinestore.service.impl.CategoryServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    @Test
    void createCategorySavesAndReturnsCategoryData() {
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryService.createCategory(
                new CategoryRequest("Audio", "Audio products")
        );

        assertEquals("Audio", response.name());
        assertEquals("Audio products", response.description());
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void updateCategoryChangesAndReturnsCategoryData() {
        Category category = new Category("Old name", "Old description");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.save(category)).thenReturn(category);

        CategoryResponse response = categoryService.updateCategory(
                1L,
                new CategoryRequest("New name", "New description")
        );

        assertEquals("New name", response.name());
        assertEquals("New description", response.description());
        verify(categoryRepository).save(category);
    }

    @Test
    void getCategoryByIdThrowsWhenCategoryDoesNotExist() {
        when(categoryRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> categoryService.getCategoryById(404L));
    }
}
