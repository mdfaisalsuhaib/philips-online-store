package com.philips.onlinestore.controller;

import com.philips.onlinestore.dto.ProductRequest;
import com.philips.onlinestore.dto.ProductResponse;
import com.philips.onlinestore.dto.PageResponse;
import com.philips.onlinestore.service.ProductService;
import com.philips.onlinestore.service.ProductService.ProductImage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/products")
@Validated
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.createProduct(request);
        URI location = URI.create("/api/v1/products/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public PageResponse<ProductResponse> getProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @Positive Long categoryId,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable
    ) {
        return PageResponse.from(productService.getProducts(search, categoryId, active, pageable));
    }

    @GetMapping("/{id}")
    public ProductResponse getProductById(@PathVariable @Positive Long id) {
        return productService.getProductById(id);
    }

    @PutMapping("/{id}")
    public ProductResponse updateProduct(
            @PathVariable @Positive Long id,
            @Valid @RequestBody ProductRequest request
    ) {
        return productService.updateProduct(id, request);
    }

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductResponse uploadProductImage(
            @PathVariable @Positive Long id,
            @RequestPart("file") MultipartFile file
    ) {
        return productService.uploadProductImage(id, file);
    }

    @PutMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductResponse replaceProductImage(
            @PathVariable @Positive Long id,
            @RequestPart("file") MultipartFile file
    ) {
        return productService.replaceProductImage(id, file);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> getProductImage(@PathVariable @Positive Long id) {
        ProductImage image = productService.getProductImage(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(image.mediaType())
                .body(image.resource());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable @Positive Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
