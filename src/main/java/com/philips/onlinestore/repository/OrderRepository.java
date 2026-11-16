package com.philips.onlinestore.repository;

import com.philips.onlinestore.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<Order> findByIdAndUser_Id(Long orderId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT orderEntity
            FROM Order orderEntity
            WHERE orderEntity.id = :orderId
              AND orderEntity.user.email = :customerEmail
            """)
    Optional<Order> findByIdAndUser_EmailForUpdate(
            @Param("orderId") Long orderId,
            @Param("customerEmail") String customerEmail
    );
}
