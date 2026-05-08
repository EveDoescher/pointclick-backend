package com.pim.ecommerce.domain.repository;

import com.pim.ecommerce.domain.entity.Payment;
import com.pim.ecommerce.domain.entity.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

    List<Payment> findByStatusOrderByCreatedAtDesc(PaymentStatus status);

    boolean existsByOrderId(Long orderId);
}