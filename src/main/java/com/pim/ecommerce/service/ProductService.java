package com.pim.ecommerce.service;

import com.pim.ecommerce.domain.entity.Product;
import com.pim.ecommerce.domain.repository.FavoriteRepository;
import com.pim.ecommerce.domain.repository.ProductRepository;
import com.pim.ecommerce.domain.repository.ReviewRepository;
import com.pim.ecommerce.dto.request.CreateProductRequest;
import com.pim.ecommerce.dto.request.UpdateProductRequest;
import com.pim.ecommerce.dto.response.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final int LOW_STOCK_THRESHOLD = 15;

    private final ProductRepository productRepository;
    private final FavoriteRepository favoriteRepository;
    private final ReviewRepository reviewRepository;
    private final NotificationService notificationService;

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        Product product = Product.builder()
                .name(request.name().trim())
                .description(request.description().trim())
                .categoryGroup(request.categoryGroup().trim())
                .category(request.category().trim())
                .brand(request.brand().trim())
                .model(normalizeOptionalText(request.model()))
                .price(request.price())
                .stockQuantity(request.stockQuantity())
                .reservedQuantity(0)
                .imageUrl(normalizeOptionalText(request.imageUrl()))
                .active(request.active())
                .build();

        return toResponse(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        return productRepository.findAllByActiveTrueOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll(
            String search,
            String category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean available
    ) {
        return findAll(search, category, null, null, minPrice, maxPrice, available, "newest");
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll(
            String search,
            String category,
            String categoryGroup,
            String brand,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean available,
            String sort
    ) {
        String searchPattern = normalizeSearchPattern(search);
        List<String> normalizedCategoryGroups = normalizeFilterList(categoryGroup);
        boolean applyCategoryGroupFilter = !normalizedCategoryGroups.isEmpty();
        String normalizedCategory = normalizeFilter(category);
        String normalizedBrand = normalizeFilter(brand);

        List<Product> products = productRepository.searchActiveProducts(
                searchPattern,
                normalizedCategory,
                applyCategoryGroupFilter,
                normalizedCategoryGroups,
                normalizedBrand,
                minPrice,
                maxPrice,
                available
        );

        return sortProducts(products, sort)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAllForAdmin(
            String search,
            String category,
            String categoryGroup,
            String brand,
            Boolean active,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean available,
            String sort
    ) {
        String searchPattern = normalizeSearchPattern(search);
        List<String> normalizedCategoryGroups = normalizeFilterList(categoryGroup);
        boolean applyCategoryGroupFilter = !normalizedCategoryGroups.isEmpty();
        String normalizedCategory = normalizeFilter(category);
        String normalizedBrand = normalizeFilter(brand);

        List<Product> products = productRepository.searchProductsForAdmin(
                searchPattern,
                normalizedCategory,
                applyCategoryGroupFilter,
                normalizedCategoryGroups,
                normalizedBrand,
                active,
                minPrice,
                maxPrice,
                available
        );

        return sortProducts(products, sort)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> findCategories() {
        return productRepository.findActiveCategories();
    }

    @Transactional(readOnly = true)
    public List<String> findAllCategoriesForAdmin() {
        return productRepository.findAllCategories();
    }

    @Transactional(readOnly = true)
    public List<String> findCategoryGroups() {
        return productRepository.findActiveCategoryGroups();
    }

    @Transactional(readOnly = true)
    public List<String> findAllCategoryGroupsForAdmin() {
        return productRepository.findAllCategoryGroups();
    }

    @Transactional(readOnly = true)
    public List<String> findBrands() {
        return productRepository.findActiveBrands();
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        Product product = productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado"));

        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse findByIdForAdmin(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado"));

        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findRelated(Long id) {
        Product product = productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado"));

        return productRepository.findRelatedByCategoryGroupOrCategory(
                        product.getId(),
                        product.getCategoryGroup(),
                        product.getCategory()
                )
                .stream()
                .limit(4)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado"));

        BigDecimal oldPrice = product.getPrice();
        int oldAvailableQuantity = calculateAvailableQuantity(product);

        product.setName(request.name().trim());
        product.setDescription(request.description().trim());
        product.setCategoryGroup(request.categoryGroup().trim());
        product.setCategory(request.category().trim());
        product.setBrand(request.brand().trim());
        product.setModel(normalizeOptionalText(request.model()));
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        product.setImageUrl(normalizeOptionalText(request.imageUrl()));
        product.setActive(request.active());

        Product updatedProduct = productRepository.save(product);

        handleProductNotifications(updatedProduct, oldPrice, oldAvailableQuantity);

        return toResponse(updatedProduct);
    }

    @Transactional
    public void delete(Long id) {
        deactivate(id);
    }

    @Transactional
    public ProductResponse deactivate(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado"));

        if (!Boolean.TRUE.equals(product.getActive())) {
            return toResponse(product);
        }

        product.setActive(false);

        return toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse activate(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado"));

        int oldAvailableQuantity = calculateAvailableQuantity(product);

        product.setActive(true);

        Product updatedProduct = productRepository.save(product);

        if (oldAvailableQuantity <= 0 && calculateAvailableQuantity(updatedProduct) > 0) {
            notificationService.notifyFavoriteProductBackInStock(updatedProduct);
        }

        return toResponse(updatedProduct);
    }

    public ProductResponse toResponse(Product product) {
        Integer reservedQuantity = product.getReservedQuantity() != null
                ? product.getReservedQuantity()
                : 0;

        Integer stockQuantity = product.getStockQuantity() != null
                ? product.getStockQuantity()
                : 0;

        Integer availableQuantity = Math.max(stockQuantity - reservedQuantity, 0);
        Boolean outOfStock = availableQuantity <= 0;
        Boolean lowStock = availableQuantity > 0 && availableQuantity <= LOW_STOCK_THRESHOLD;

        Long favoriteCount = favoriteRepository.countByProductId(product.getId());
        Long reviewCount = reviewRepository.countByProductIdAndActiveTrue(product.getId());
        Double averageRating = reviewRepository.averageRatingByProductId(product.getId());

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getCategoryGroup(),
                product.getCategory(),
                product.getBrand(),
                product.getModel(),
                product.getPrice(),
                stockQuantity,
                reservedQuantity,
                availableQuantity,
                product.getImageUrl(),
                product.getActive(),
                outOfStock,
                lowStock,
                favoriteCount,
                reviewCount,
                averageRating != null ? averageRating : 0.0,
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    private void handleProductNotifications(
            Product updatedProduct,
            BigDecimal oldPrice,
            int oldAvailableQuantity
    ) {
        if (oldPrice != null
                && updatedProduct.getPrice() != null
                && updatedProduct.getPrice().compareTo(oldPrice) < 0
                && Boolean.TRUE.equals(updatedProduct.getActive())) {
            notificationService.notifyFavoriteProductPromotion(
                    updatedProduct,
                    oldPrice,
                    updatedProduct.getPrice()
            );
        }

        int newAvailableQuantity = calculateAvailableQuantity(updatedProduct);

        if (oldAvailableQuantity <= 0
                && newAvailableQuantity > 0
                && Boolean.TRUE.equals(updatedProduct.getActive())) {
            notificationService.notifyFavoriteProductBackInStock(updatedProduct);
        }
    }

    private int calculateAvailableQuantity(Product product) {
        Integer stockQuantity = product.getStockQuantity() != null
                ? product.getStockQuantity()
                : 0;

        Integer reservedQuantity = product.getReservedQuantity() != null
                ? product.getReservedQuantity()
                : 0;

        return Math.max(stockQuantity - reservedQuantity, 0);
    }

    private List<Product> sortProducts(List<Product> products, String sort) {
        if (sort == null || sort.isBlank() || sort.equalsIgnoreCase("newest")) {
            return products;
        }

        return switch (sort.trim().toLowerCase(Locale.ROOT)) {
            case "price_asc" -> products.stream()
                    .sorted(Comparator.comparing(Product::getPrice))
                    .toList();
            case "price_desc" -> products.stream()
                    .sorted(Comparator.comparing(Product::getPrice).reversed())
                    .toList();
            case "name_asc" -> products.stream()
                    .sorted(Comparator.comparing(product -> product.getName().toLowerCase(Locale.ROOT)))
                    .toList();
            default -> products;
        };
    }

    private String normalizeSearchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }

        return "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private String normalizeFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> normalizeFilterList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .map(item -> item.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}