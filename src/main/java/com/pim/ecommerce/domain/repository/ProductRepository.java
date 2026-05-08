package com.pim.ecommerce.domain.repository;

import com.pim.ecommerce.domain.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndActiveTrue(Long id);

    List<Product> findAllByActiveTrue();

    List<Product> findAllByActiveTrueOrderByCreatedAtDesc();

    List<Product> findAllByOrderByCreatedAtDesc();

    @Query("""
            SELECT p
            FROM Product p
            WHERE p.active = true
              AND (
                    :searchPattern IS NULL
                    OR LOWER(p.name) LIKE :searchPattern
                    OR LOWER(p.description) LIKE :searchPattern
                    OR LOWER(p.brand) LIKE :searchPattern
                    OR LOWER(p.model) LIKE :searchPattern
                    OR LOWER(p.category) LIKE :searchPattern
                    OR LOWER(p.categoryGroup) LIKE :searchPattern
              )
              AND (
                    :category IS NULL
                    OR LOWER(p.category) = :category
              )
              AND (
                    :applyCategoryGroupFilter = false
                    OR LOWER(p.categoryGroup) IN :categoryGroups
              )
              AND (
                    :brand IS NULL
                    OR LOWER(p.brand) = :brand
              )
              AND (
                    :minPrice IS NULL
                    OR p.price >= :minPrice
              )
              AND (
                    :maxPrice IS NULL
                    OR p.price <= :maxPrice
              )
              AND (
                    :available IS NULL
                    OR :available = false
                    OR (p.stockQuantity - COALESCE(p.reservedQuantity, 0)) > 0
              )
            ORDER BY p.createdAt DESC
            """)
    List<Product> searchActiveProducts(
            @Param("searchPattern") String searchPattern,
            @Param("category") String category,
            @Param("applyCategoryGroupFilter") boolean applyCategoryGroupFilter,
            @Param("categoryGroups") List<String> categoryGroups,
            @Param("brand") String brand,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("available") Boolean available
    );

    @Query("""
            SELECT p
            FROM Product p
            WHERE (
                    :searchPattern IS NULL
                    OR LOWER(p.name) LIKE :searchPattern
                    OR LOWER(p.description) LIKE :searchPattern
                    OR LOWER(p.brand) LIKE :searchPattern
                    OR LOWER(p.model) LIKE :searchPattern
                    OR LOWER(p.categoryGroup) LIKE :searchPattern
                    OR LOWER(p.category) LIKE :searchPattern
              )
              AND (
                    :category IS NULL
                    OR LOWER(p.category) = :category
              )
              AND (
                    :applyCategoryGroupFilter = false
                    OR LOWER(p.categoryGroup) IN :categoryGroups
              )
              AND (
                    :brand IS NULL
                    OR LOWER(p.brand) = :brand
              )
              AND (
                    :active IS NULL
                    OR p.active = :active
              )
              AND (
                    :minPrice IS NULL
                    OR p.price >= :minPrice
              )
              AND (
                    :maxPrice IS NULL
                    OR p.price <= :maxPrice
              )
              AND (
                    :available IS NULL
                    OR :available = false
                    OR (p.stockQuantity - COALESCE(p.reservedQuantity, 0)) > 0
              )
            ORDER BY p.createdAt DESC
            """)
    List<Product> searchProductsForAdmin(
            @Param("searchPattern") String searchPattern,
            @Param("category") String category,
            @Param("applyCategoryGroupFilter") boolean applyCategoryGroupFilter,
            @Param("categoryGroups") List<String> categoryGroups,
            @Param("brand") String brand,
            @Param("active") Boolean active,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("available") Boolean available
    );

    @Query("""
            SELECT DISTINCT p.category
            FROM Product p
            WHERE p.active = true
              AND p.category IS NOT NULL
              AND TRIM(p.category) <> ''
            ORDER BY p.category ASC
            """)
    List<String> findActiveCategories();

    @Query("""
            SELECT DISTINCT p.category
            FROM Product p
            WHERE p.category IS NOT NULL
              AND TRIM(p.category) <> ''
            ORDER BY p.category ASC
            """)
    List<String> findAllCategories();

    @Query("""
            SELECT DISTINCT p.brand
            FROM Product p
            WHERE p.active = true
              AND p.brand IS NOT NULL
              AND TRIM(p.brand) <> ''
            ORDER BY p.brand ASC
            """)
    List<String> findActiveBrands();

    @Query("""
            SELECT p
            FROM Product p
            WHERE p.active = true
              AND p.id <> :productId
              AND (
                    LOWER(p.categoryGroup) = LOWER(:categoryGroup)
                    OR LOWER(p.category) = LOWER(:category)
              )
            ORDER BY p.createdAt DESC
            """)
    List<Product> findRelatedByCategoryGroupOrCategory(
            @Param("productId") Long productId,
            @Param("categoryGroup") String categoryGroup,
            @Param("category") String category
    );

    long countByActiveTrue();

    long countByActiveFalse();

    @Query("""
            SELECT COUNT(p)
            FROM Product p
            WHERE p.active = true
              AND (p.stockQuantity - COALESCE(p.reservedQuantity, 0)) <= 0
            """)
    long countOutOfStockActiveProducts();

    @Query("""
            SELECT COUNT(p)
            FROM Product p
            WHERE p.active = true
              AND (p.stockQuantity - COALESCE(p.reservedQuantity, 0)) > 0
              AND (p.stockQuantity - COALESCE(p.reservedQuantity, 0)) <= :threshold
            """)
    long countLowStockActiveProducts(@Param("threshold") int threshold);

    @Query("""
            SELECT DISTINCT p.categoryGroup
            FROM Product p
            WHERE p.active = true
              AND p.categoryGroup IS NOT NULL
              AND TRIM(p.categoryGroup) <> ''
            ORDER BY p.categoryGroup ASC
            """)
    List<String> findActiveCategoryGroups();

    @Query("""
            SELECT DISTINCT p.categoryGroup
            FROM Product p
            WHERE p.categoryGroup IS NOT NULL
              AND TRIM(p.categoryGroup) <> ''
            ORDER BY p.categoryGroup ASC
            """)
    List<String> findAllCategoryGroups();
}