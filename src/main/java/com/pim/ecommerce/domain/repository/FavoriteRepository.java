package com.pim.ecommerce.domain.repository;

import com.pim.ecommerce.domain.entity.Favorite;
import com.pim.ecommerce.domain.entity.Product;
import com.pim.ecommerce.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Favorite> findByProductId(Long productId);

    Optional<Favorite> findByUserIdAndProductId(Long userId, Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    void deleteByUserIdAndProductId(Long userId, Long productId);

    @Query("""
            SELECT f.user
            FROM Favorite f
            WHERE f.product.id = :productId
              AND f.user.active = true
            """)
    List<User> findActiveUsersByProductId(@Param("productId") Long productId);

    @Query("""
            SELECT f.product
            FROM Favorite f
            WHERE f.user.id = :userId
              AND f.product.active = true
            ORDER BY f.createdAt DESC
            """)
    List<Product> findActiveFavoriteProductsByUserId(@Param("userId") Long userId);

    long countByUserId(Long userId);

    long countByProductId(Long productId);
}