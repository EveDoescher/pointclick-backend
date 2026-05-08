package com.pim.ecommerce.domain.repository;

import com.pim.ecommerce.domain.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductIdAndActiveTrueOrderByCreatedAtDesc(Long productId);

    List<Review> findByUserIdAndActiveTrueOrderByCreatedAtDesc(Long userId);

    Optional<Review> findByIdAndActiveTrue(Long id);

    boolean existsByUserIdAndOrderIdAndProductIdAndActiveTrue(
            Long userId,
            Long orderId,
            Long productId
    );

    long countByProductIdAndActiveTrue(Long productId);

    @Query("""
            SELECT COALESCE(AVG(r.rating), 0)
            FROM Review r
            WHERE r.product.id = :productId
              AND r.active = true
            """)
    Double averageRatingByProductId(@Param("productId") Long productId);

    @Query("""
            SELECT DISTINCT r
            FROM Review r
            LEFT JOIN FETCH r.images
            WHERE r.product.id = :productId
              AND r.active = true
              AND (:rating IS NULL OR r.rating = :rating)
              AND (
                    :withComment = false
                    OR r.comment IS NOT NULL AND TRIM(r.comment) <> ''
              )
              AND (
                    :withMedia = false
                    OR SIZE(r.images) > 0
              )
            ORDER BY r.createdAt DESC
            """)
    List<Review> searchByProduct(
            @Param("productId") Long productId,
            @Param("rating") Integer rating,
            @Param("withComment") boolean withComment,
            @Param("withMedia") boolean withMedia
    );

    @Query("""
            SELECT COUNT(r)
            FROM Review r
            WHERE r.product.id = :productId
              AND r.active = true
              AND r.rating = :rating
            """)
    Long countByProductIdAndRating(
            @Param("productId") Long productId,
            @Param("rating") Integer rating
    );

    @Query("""
            SELECT COUNT(r)
            FROM Review r
            WHERE r.product.id = :productId
              AND r.active = true
              AND r.comment IS NOT NULL
              AND TRIM(r.comment) <> ''
            """)
    Long countWithCommentsByProductId(@Param("productId") Long productId);

    @Query("""
            SELECT COUNT(DISTINCT r)
            FROM Review r
            JOIN r.images i
            WHERE r.product.id = :productId
              AND r.active = true
            """)
    Long countWithMediaByProductId(@Param("productId") Long productId);
}