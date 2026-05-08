package com.pim.ecommerce.service;

import com.pim.ecommerce.domain.entity.Product;
import com.pim.ecommerce.domain.repository.FavoriteRepository;
import com.pim.ecommerce.domain.repository.ProductRepository;
import com.pim.ecommerce.domain.repository.ReviewRepository;
import com.pim.ecommerce.dto.request.CreateProductRequest;
import com.pim.ecommerce.dto.request.UpdateProductRequest;
import com.pim.ecommerce.dto.response.ProductResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ProductService productService;

    @Test
    void shouldCreateProductWithNormalizedFieldsAndReservedQuantityZero() {
        CreateProductRequest request = new CreateProductRequest(
                " Teclado Mecânico ",
                " Descrição detalhada ",
                " Periféricos ",
                " Teclados ",
                " PointClick ",
                " PC Keys ",
                new BigDecimal("399.90"),
                15,
                " /uploads/products/teclado.png ",
                true
        );

        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(1L);
            return product;
        });
        mockProductMetrics(1L);

        ProductResponse response = productService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Teclado Mecânico");
        assertThat(response.description()).isEqualTo("Descrição detalhada");
        assertThat(response.categoryGroup()).isEqualTo("Periféricos");
        assertThat(response.category()).isEqualTo("Teclados");
        assertThat(response.brand()).isEqualTo("PointClick");
        assertThat(response.model()).isEqualTo("PC Keys");
        assertThat(response.reservedQuantity()).isZero();
        assertThat(response.availableQuantity()).isEqualTo(15);
        assertThat(response.outOfStock()).isFalse();
        assertThat(response.lowStock()).isTrue();
    }

    @Test
    void shouldCalculateAvailabilityAndOutOfStockInResponse() {
        Product product = product(1L, "Teclado", new BigDecimal("100.00"), 4, 4, true);
        mockProductMetrics(1L);

        ProductResponse response = productService.toResponse(product);

        assertThat(response.stockQuantity()).isEqualTo(4);
        assertThat(response.reservedQuantity()).isEqualTo(4);
        assertThat(response.availableQuantity()).isZero();
        assertThat(response.outOfStock()).isTrue();
        assertThat(response.lowStock()).isFalse();
    }

    @Test
    void shouldSortProductsByPriceAscending() {
        Product expensive = product(1L, "Notebook", new BigDecimal("3500.00"), 10, 0, true);
        Product cheap = product(2L, "Mouse", new BigDecimal("99.90"), 10, 0, true);

        when(productRepository.searchActiveProducts(
                eq("%rgb%"),
                eq("teclados"),
                eq(true),
                eq(List.of("periféricos", "acessórios")),
                eq("pointclick"),
                eq(new BigDecimal("10.00")),
                eq(new BigDecimal("4000.00")),
                eq(true)
        )).thenReturn(List.of(expensive, cheap));
        mockProductMetrics(1L);
        mockProductMetrics(2L);

        List<ProductResponse> responses = productService.findAll(
                " RGB ",
                " Teclados ",
                " Periféricos, Acessórios ",
                " PointClick ",
                new BigDecimal("10.00"),
                new BigDecimal("4000.00"),
                true,
                "price_asc"
        );

        assertThat(responses).extracting(ProductResponse::name)
                .containsExactly("Mouse", "Notebook");
    }

    @Test
    void shouldUpdateProductAndNotifyPromotionWhenPriceDrops() {
        Product product = product(1L, "Teclado", new BigDecimal("500.00"), 10, 0, true);
        UpdateProductRequest request = new UpdateProductRequest(
                "Teclado Novo",
                "Descrição nova",
                "Periféricos",
                "Teclados",
                "PointClick",
                "Modelo Novo",
                new BigDecimal("399.90"),
                12,
                "/uploads/products/novo.png",
                true
        );

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mockProductMetrics(1L);

        ProductResponse response = productService.update(1L, request);

        assertThat(response.name()).isEqualTo("Teclado Novo");
        assertThat(response.price()).isEqualByComparingTo("399.90");
        assertThat(response.availableQuantity()).isEqualTo(12);
        verify(notificationService).notifyFavoriteProductPromotion(product, new BigDecimal("500.00"), new BigDecimal("399.90"));
    }

    @Test
    void shouldNotNotifyPromotionWhenPriceIncreases() {
        Product product = product(1L, "Teclado", new BigDecimal("399.90"), 10, 0, true);
        UpdateProductRequest request = new UpdateProductRequest(
                "Teclado Novo",
                "Descrição nova",
                "Periféricos",
                "Teclados",
                "PointClick",
                "Modelo Novo",
                new BigDecimal("500.00"),
                10,
                null,
                true
        );

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mockProductMetrics(1L);

        productService.update(1L, request);

        verify(notificationService, never()).notifyFavoriteProductPromotion(any(), any(), any());
    }

    @Test
    void shouldDeactivateProductIdempotently() {
        Product inactive = product(1L, "Teclado", new BigDecimal("399.90"), 10, 0, false);
        when(productRepository.findById(1L)).thenReturn(Optional.of(inactive));
        mockProductMetrics(1L);

        ProductResponse response = productService.deactivate(1L);

        assertThat(response.active()).isFalse();
        verify(productRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenProductNotFound() {
        when(productRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Produto não encontrado");
    }

    private Product product(Long id, String name, BigDecimal price, Integer stock, Integer reserved, Boolean active) {
        return Product.builder()
                .id(id)
                .name(name)
                .description("Descrição")
                .categoryGroup("Periféricos")
                .category("Teclados")
                .brand("PointClick")
                .model("Modelo")
                .price(price)
                .stockQuantity(stock)
                .reservedQuantity(reserved)
                .imageUrl("/uploads/products/teste.png")
                .active(active)
                .build();
    }

    private void mockProductMetrics(Long productId) {
        when(favoriteRepository.countByProductId(productId)).thenReturn(2L);
        when(reviewRepository.countByProductIdAndActiveTrue(productId)).thenReturn(3L);
        when(reviewRepository.averageRatingByProductId(productId)).thenReturn(4.5);
    }

    @Test
    void shouldFindAllActiveProductsWithoutFilters() {
        Product product = mutationProduct(1L, "Teclado", new BigDecimal("100.00"), 20, 5, true);
        when(productRepository.searchActiveProducts(
                isNull(),
                isNull(),
                eq(false),
                eq(List.of()),
                isNull(),
                isNull(),
                isNull(),
                isNull()
        )).thenReturn(List.of(product));
        mutationMockMetrics(1L);

        List<ProductResponse> response = productService.findAll(null, null, null, null, null, null, null, null);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().availableQuantity()).isEqualTo(15);
        assertThat(response.getFirst().lowStock()).isTrue();
    }

    @Test
    void shouldSortProductsByPriceDescending() {
        Product cheap = mutationProduct(1L, "Mouse", new BigDecimal("99.90"), 10, 0, true);
        Product expensive = mutationProduct(2L, "Notebook", new BigDecimal("3500.00"), 10, 0, true);
        when(productRepository.searchActiveProducts(isNull(), isNull(), eq(false), eq(List.of()), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(cheap, expensive));
        mutationMockMetrics(1L, 2L);

        List<ProductResponse> response = productService.findAll(null, null, null, null, null, null, null, "price_desc");

        assertThat(response).extracting(ProductResponse::name).containsExactly("Notebook", "Mouse");
    }

    @Test
    void shouldSortProductsByNameAscendingIgnoringCase() {
        Product keyboard = mutationProduct(1L, "teclado", new BigDecimal("100.00"), 10, 0, true);
        Product mouse = mutationProduct(2L, "Mouse", new BigDecimal("99.90"), 10, 0, true);
        when(productRepository.searchActiveProducts(isNull(), isNull(), eq(false), eq(List.of()), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(keyboard, mouse));
        mutationMockMetrics(1L, 2L);

        List<ProductResponse> response = productService.findAll(null, null, null, null, null, null, null, "name_asc");

        assertThat(response).extracting(ProductResponse::name).containsExactly("Mouse", "teclado");
    }

    @Test
    void shouldFindProductsForAdminWithNormalizedFilters() {
        Product product = mutationProduct(1L, "Teclado", new BigDecimal("399.90"), 15, 0, false);
        when(productRepository.searchProductsForAdmin(
                eq("%rgb%"),
                eq("teclados"),
                eq(true),
                eq(List.of("periféricos", "acessórios")),
                eq("pointclick"),
                eq(false),
                eq(new BigDecimal("10.00")),
                eq(new BigDecimal("500.00")),
                eq(true)
        )).thenReturn(List.of(product));
        mutationMockMetrics(1L);

        List<ProductResponse> response = productService.findAllForAdmin(
                " RGB ",
                " Teclados ",
                " Periféricos, Acessórios, Periféricos ",
                " PointClick ",
                false,
                new BigDecimal("10.00"),
                new BigDecimal("500.00"),
                true,
                "newest"
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().active()).isFalse();
    }

    @Test
    void shouldFindRelatedProductsAndLimitToFour() {
        Product base = mutationProduct(1L, "Base", new BigDecimal("100.00"), 10, 0, true);
        Product p2 = mutationProduct(2L, "P2", new BigDecimal("100.00"), 10, 0, true);
        Product p3 = mutationProduct(3L, "P3", new BigDecimal("100.00"), 10, 0, true);
        Product p4 = mutationProduct(4L, "P4", new BigDecimal("100.00"), 10, 0, true);
        Product p5 = mutationProduct(5L, "P5", new BigDecimal("100.00"), 10, 0, true);
        Product p6 = mutationProduct(6L, "P6", new BigDecimal("100.00"), 10, 0, true);

        when(productRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(base));
        when(productRepository.findRelatedByCategoryGroupOrCategory(1L, "Periféricos", "Teclados"))
                .thenReturn(List.of(p2, p3, p4, p5, p6));
        mutationMockMetrics(2L, 3L, 4L, 5L);

        List<ProductResponse> response = productService.findRelated(1L);

        assertThat(response).hasSize(4);
        assertThat(response).extracting(ProductResponse::id).containsExactly(2L, 3L, 4L, 5L);
    }

    @Test
    void shouldActivateProductAndNotifyBackInStockWhenAvailableQuantityBecomesPositive() {
        Product product = mutationProduct(1L, "Teclado", new BigDecimal("399.90"), 10, 10, false);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setReservedQuantity(0);
            return saved;
        });
        mutationMockMetrics(1L);

        ProductResponse response = productService.activate(1L);

        assertThat(response.active()).isTrue();
        assertThat(response.availableQuantity()).isEqualTo(10);
        verify(notificationService).notifyFavoriteProductBackInStock(product);
    }

    @Test
    void shouldUpdateProductAndNotifyBackInStockWhenStockBecomesAvailable() {
        Product product = mutationProduct(1L, "Teclado", new BigDecimal("399.90"), 5, 5, true);
        UpdateProductRequest request = mutationUpdateRequest(new BigDecimal("399.90"), 10, true);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mutationMockMetrics(1L);

        ProductResponse response = productService.update(1L, request);

        assertThat(response.availableQuantity()).isEqualTo(5);
        verify(notificationService).notifyFavoriteProductBackInStock(product);
        verify(notificationService, never()).notifyFavoriteProductPromotion(any(), any(), any());
    }

    @Test
    void shouldDeactivateActiveProductAndSaveIt() {
        Product product = mutationProduct(1L, "Teclado", new BigDecimal("399.90"), 10, 0, true);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mutationMockMetrics(1L);

        ProductResponse response = productService.deactivate(1L);

        assertThat(response.active()).isFalse();
        verify(productRepository).save(product);
    }

    @Test
    void shouldReturnZeroAverageRatingWhenRepositoryReturnsNull() {
        Product product = mutationProduct(1L, "Teclado", new BigDecimal("399.90"), null, null, true);
        when(favoriteRepository.countByProductId(1L)).thenReturn(0L);
        when(reviewRepository.countByProductIdAndActiveTrue(1L)).thenReturn(0L);
        when(reviewRepository.averageRatingByProductId(1L)).thenReturn(null);

        ProductResponse response = productService.toResponse(product);

        assertThat(response.stockQuantity()).isZero();
        assertThat(response.reservedQuantity()).isZero();
        assertThat(response.availableQuantity()).isZero();
        assertThat(response.outOfStock()).isTrue();
        assertThat(response.averageRating()).isEqualTo(0.0);
    }

    @Test
    void shouldDelegateCategoryBrandAndGroupLookups() {
        when(productRepository.findActiveCategories()).thenReturn(List.of("Teclados"));
        when(productRepository.findAllCategories()).thenReturn(List.of("Mouses", "Teclados"));
        when(productRepository.findActiveCategoryGroups()).thenReturn(List.of("Periféricos"));
        when(productRepository.findAllCategoryGroups()).thenReturn(List.of("Computadores", "Periféricos"));
        when(productRepository.findActiveBrands()).thenReturn(List.of("PointClick"));

        assertThat(productService.findCategories()).containsExactly("Teclados");
        assertThat(productService.findAllCategoriesForAdmin()).containsExactly("Mouses", "Teclados");
        assertThat(productService.findCategoryGroups()).containsExactly("Periféricos");
        assertThat(productService.findAllCategoryGroupsForAdmin()).containsExactly("Computadores", "Periféricos");
        assertThat(productService.findBrands()).containsExactly("PointClick");
    }

    private UpdateProductRequest mutationUpdateRequest(BigDecimal price, Integer stockQuantity, Boolean active) {
        return new UpdateProductRequest(
                "Teclado Novo",
                "Descrição nova",
                "Periféricos",
                "Teclados",
                "PointClick",
                "Modelo Novo",
                price,
                stockQuantity,
                "/uploads/products/novo.png",
                active
        );
    }

    private Product mutationProduct(Long id, String name, BigDecimal price, Integer stock, Integer reserved, Boolean active) {
        return Product.builder()
                .id(id)
                .name(name)
                .description("Descrição")
                .categoryGroup("Periféricos")
                .category("Teclados")
                .brand("PointClick")
                .model("Modelo")
                .price(price)
                .stockQuantity(stock)
                .reservedQuantity(reserved)
                .imageUrl("/uploads/products/teste.png")
                .active(active)
                .build();
    }

    private void mutationMockMetrics(Long... productIds) {
        for (Long productId : productIds) {
            when(favoriteRepository.countByProductId(productId)).thenReturn(2L);
            when(reviewRepository.countByProductIdAndActiveTrue(productId)).thenReturn(3L);
            when(reviewRepository.averageRatingByProductId(productId)).thenReturn(4.5);
        }
    }
}
