package com.philips.onlinestore.repository;

import com.philips.onlinestore.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findAllByOrder_Id(Long orderId);

    Optional<Payment> findByOrder_IdAndIdempotencyKey(Long orderId, String idempotencyKey);

    List<Payment> findAllByOrder_IdOrderByCreatedAtAsc(Long orderId);
}
