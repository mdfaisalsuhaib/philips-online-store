package com.philips.onlinestore.service;

import com.philips.onlinestore.dto.ProductRequest;
import com.philips.onlinestore.dto.ProductResponse;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

public interface ProductService {

    ProductResponse createProduct(ProductRequest request);

    ProductResponse getProductById(Long id);

    Page<ProductResponse> getProducts(
            String search,
            Long categoryId,
            Boolean active,
            Pageable pageable
    );

    ProductResponse updateProduct(Long id, ProductRequest request);

    ProductResponse uploadProductImage(Long id, MultipartFile image);

    ProductResponse replaceProductImage(Long id, MultipartFile image);

    ProductImage getProductImage(Long id);

    void deleteProduct(Long id);

    record ProductImage(Resource resource, MediaType mediaType) {
    }
}
