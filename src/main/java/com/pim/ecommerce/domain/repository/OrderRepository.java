package com.pim.ecommerce.domain.repository;

import com.pim.ecommerce.domain.entity.Order;
import com.pim.ecommerce.domain.entity.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Order> findFirstByUserIdAndStatusOrderByCreatedAtDesc(Long userId, OrderStatus status);

    List<Order> findAllByOrderByCreatedAtDesc();

    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    long countByStatus(OrderStatus status);

    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0)
            FROM Order o
            WHERE o.status IN :statuses
            """)
    BigDecimal sumTotalAmountByStatusIn(@Param("statuses") Collection<OrderStatus> statuses);

    @Query("""
            SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END
            FROM Order o
            JOIN o.items i
            WHERE o.user.id = :userId
              AND i.product.id = :productId
              AND o.status = :status
            """)
    boolean existsFinishedOrderWithProduct(
            @Param("userId") Long userId,
            @Param("productId") Long productId,
            @Param("status") OrderStatus status
    );

    @Query("""
            SELECT DISTINCT o
            FROM Order o
            JOIN o.items i
            WHERE i.product.id = :productId
              AND o.status = :status
            ORDER BY o.createdAt DESC
            """)
    List<Order> findOrdersByProductIdAndStatus(
            @Param("productId") Long productId,
            @Param("status") OrderStatus status
    );
}