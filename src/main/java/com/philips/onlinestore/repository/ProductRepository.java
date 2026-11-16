package com.philips.onlinestore.repository;

import com.philips.onlinestore.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT product FROM Product product WHERE product.id = :productId")
    Optional<Product> findByIdForUpdate(@Param("productId") Long productId);

    @Query("""
            SELECT product
            FROM Product product
            WHERE (:search = ''
                   OR LOWER(product.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(product.sku) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:categoryId IS NULL OR product.category.id = :categoryId)
              AND (:active IS NULL OR product.active = :active)
            """)
    Page<Product> search(
            @Param("search") String search,
            @Param("categoryId") Long categoryId,
            @Param("active") Boolean active,
            Pageable pageable
    );
}
