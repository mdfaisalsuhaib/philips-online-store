package com.philips.onlinestore.service.impl;

import com.philips.onlinestore.dto.ProductRequest;
import com.philips.onlinestore.dto.ProductResponse;
import com.philips.onlinestore.entity.Category;
import com.philips.onlinestore.entity.Product;
import com.philips.onlinestore.repository.CategoryRepository;
import com.philips.onlinestore.repository.ProductRepository;
import com.philips.onlinestore.service.ProductService;
import com.philips.onlinestore.service.ProductService.ProductImage;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageStorageService productImageStorageService;

    public ProductServiceImpl(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            ProductImageStorageService productImageStorageService
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productImageStorageService = productImageStorageService;
    }

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Category category = findCategoryById(request.categoryId());
        Product product = new Product(
                request.name(),
                request.sku(),
                request.description(),
                request.price(),
                request.stockQuantity(),
                category
        );
        product.setActive(request.active());
        return toResponse(productRepository.save(product));
    }

    @Override
    public ProductResponse getProductById(Long id) {
        return toResponse(findProductById(id));
    }

    @Override
    public Page<ProductResponse> getProducts(
            String search,
            Long categoryId,
            Boolean active,
            Pageable pageable
    ) {
        String normalizedSearch = search == null ? "" : search.trim();
        return productRepository.search(normalizedSearch, categoryId, active, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = findProductById(id);
        Category category = findCategoryById(request.categoryId());

        product.setName(request.name());
        product.setSku(request.sku());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        product.setActive(request.active());
        product.setCategory(category);

        return toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse uploadProductImage(Long id, MultipartFile image) {
        return saveProductImage(id, image);
    }

    @Override
    @Transactional
    public ProductResponse replaceProductImage(Long id, MultipartFile image) {
        return saveProductImage(id, image);
    }

    @Override
    public ProductImage getProductImage(Long id) {
        Product product = findProductById(id);
        String imageFilename = product.getImageUrl();
        if (imageFilename == null || imageFilename.isBlank()) {
            throw new ProductImageStorageService.ImageNotFoundException(
                    "Product image was not found for product with id: " + id
            );
        }

        ProductImageStorageService.StoredImage storedImage = productImageStorageService.load(imageFilename);
        return new ProductImage(storedImage.resource(), storedImage.mediaType());
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        productRepository.delete(findProductById(id));
    }

    private ProductResponse saveProductImage(Long id, MultipartFile image) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Product image updates require an active transaction.");
        }

        Product product = findProductById(id);
        String oldImageFilename = product.getImageUrl();
        String newImageFilename = productImageStorageService.store(image);
        product.setImageUrl(newImageFilename);

        try {
            Product savedProduct = productRepository.save(product);
            registerImageCleanup(newImageFilename, oldImageFilename);
            return toResponse(savedProduct);
        } catch (RuntimeException exception) {
            deleteImageSafely(newImageFilename, "new image after product update failure");
            throw exception;
        }
    }

    private void registerImageCleanup(String newImageFilename, String oldImageFilename) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (oldImageFilename != null && !oldImageFilename.isBlank()) {
                    deleteImageSafely(oldImageFilename, "replaced image after transaction commit");
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteImageSafely(newImageFilename, "new image after transaction rollback");
                }
            }
        });
    }

    private void deleteImageSafely(String filename, String operation) {
        try {
            productImageStorageService.delete(filename);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not delete product image {} ({})", filename, operation, exception);
        }
    }

    private Product findProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + id));
    }

    private Category findCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with id: " + id));
    }

    private ProductResponse toResponse(Product product) {
        Category category = product.getCategory();
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.isActive(),
                category.getId(),
                category.getName(),
                product.getImageUrl() == null || product.getImageUrl().isBlank()
                        ? null
                        : "/api/v1/products/" + product.getId() + "/image"
        );
    }
}
