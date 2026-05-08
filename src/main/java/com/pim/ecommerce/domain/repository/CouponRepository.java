package com.pim.ecommerce.domain.repository;

import com.pim.ecommerce.domain.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCode(String code);

    Optional<Coupon> findByCodeAndActiveTrue(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<Coupon> findAllByOrderByCreatedAtDesc();

    List<Coupon> findByActiveOrderByCreatedAtDesc(Boolean active);
}