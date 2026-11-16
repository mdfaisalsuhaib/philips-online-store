package com.philips.onlinestore.service;

import com.philips.onlinestore.dto.ProductRequest;
import com.philips.onlinestore.dto.ProductResponse;
import com.philips.onlinestore.entity.Category;
import com.philips.onlinestore.entity.Product;
import com.philips.onlinestore.repository.CategoryRepository;
import com.philips.onlinestore.repository.ProductRepository;
import com.philips.onlinestore.service.impl.ProductImageStorageService;
import com.philips.onlinestore.service.impl.ProductServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductImageStorageService productImageStorageService;

    @InjectMocks
    private ProductServiceImpl productService;

    @AfterEach
    void clearTransactionSynchronizationState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void createProductUsesCategoryAndReturnsProductData() {
        Category category = new Category("Audio", null);
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.createProduct(productRequest(3L));

        assertEquals("Headphones", response.name());
        assertEquals("PH-100", response.sku());
        assertEquals("Audio", response.categoryName());
        assertEquals(new BigDecimal("49.99"), response.price());
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void createProductThrowsWhenCategoryDoesNotExist() {
        when(categoryRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> productService.createProduct(productRequest(404L))
        );
    }

    @Test
    void getProductsMapsPagedResultsToResponses() {
        Category category = new Category("Audio", null);
        Product product = new Product(
                "Headphones",
                "PH-100",
                "Wireless headphones",
                new BigDecimal("49.99"),
                12,
                category
        );
        Pageable pageable = Pageable.unpaged();
        Page<Product> productPage = new PageImpl<>(List.of(product));
        when(productRepository.search("head", 3L, true, pageable)).thenReturn(productPage);

        Page<ProductResponse> response = productService.getProducts("head", 3L, true, pageable);

        assertEquals(1, response.getTotalElements());
        assertEquals("Headphones", response.getContent().getFirst().name());
        assertEquals("Audio", response.getContent().getFirst().categoryName());
    }

    @Test
    void getProductsUsesEmptyStringWhenSearchIsOmitted() {
        Pageable pageable = Pageable.unpaged();
        when(productRepository.search("", null, null, pageable))
                .thenReturn(Page.empty(pageable));

        Page<ProductResponse> response = productService.getProducts(null, null, null, pageable);

        assertEquals(0, response.getTotalElements());
        verify(productRepository).search(eq(""), eq(null), eq(null), eq(pageable));
    }

    @Test
    void replaceProductImageDeletesOldImageAfterCommit() {
        beginTestTransaction();
        Product product = productWithImage("old-image.png");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productImageStorageService.store(any(MultipartFile.class))).thenReturn("new-image.png");
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        productService.replaceProductImage(1L, mock(MultipartFile.class));

        assertEquals("new-image.png", product.getImageUrl());
        verify(productRepository).save(product);
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, synchronizations.size());
        TransactionSynchronization synchronization = synchronizations.getFirst();
        assertNotNull(synchronization);

        synchronization.afterCommit();
        synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        verify(productImageStorageService).delete("old-image.png");
        verify(productImageStorageService, never()).delete("new-image.png");
    }

    @Test
    void replaceProductImageDeletesNewImageAfterRollbackAndKeepsOldImage() {
        beginTestTransaction();
        Product product = productWithImage("old-image.png");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productImageStorageService.store(any(MultipartFile.class))).thenReturn("new-image.png");
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        productService.replaceProductImage(1L, mock(MultipartFile.class));

        assertEquals("new-image.png", product.getImageUrl());
        verify(productRepository).save(product);
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, synchronizations.size());
        TransactionSynchronization synchronization = synchronizations.getFirst();
        assertNotNull(synchronization);

        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(productImageStorageService).delete("new-image.png");
        verify(productImageStorageService, never()).delete("old-image.png");
    }

    private ProductRequest productRequest(Long categoryId) {
        return new ProductRequest(
                "Headphones",
                "PH-100",
                "Wireless headphones",
                new BigDecimal("49.99"),
                12,
                true,
                categoryId
        );
    }

    private void beginTestTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private Product productWithImage(String imageFilename) {
        Category category = new Category("Audio", null);
        Product product = new Product(
                "Headphones",
                "PH-100",
                "Wireless headphones",
                new BigDecimal("49.99"),
                12,
                category
        );
        product.setImageUrl(imageFilename);
        return product;
    }
}
