package com.pim.ecommerce.service;

import com.pim.ecommerce.domain.entity.Order;
import com.pim.ecommerce.domain.entity.OrderItem;
import com.pim.ecommerce.domain.entity.Product;
import com.pim.ecommerce.domain.entity.Review;
import com.pim.ecommerce.domain.entity.ReviewImage;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.entity.enums.OrderStatus;
import com.pim.ecommerce.domain.entity.enums.UserRole;
import com.pim.ecommerce.domain.repository.OrderRepository;
import com.pim.ecommerce.domain.repository.ProductRepository;
import com.pim.ecommerce.domain.repository.ReviewImageRepository;
import com.pim.ecommerce.domain.repository.ReviewRepository;
import com.pim.ecommerce.domain.repository.UserRepository;
import com.pim.ecommerce.dto.response.ReviewResponse;
import com.pim.ecommerce.security.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewImageRepository reviewImageRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    void shouldCreateReviewForFinishedOrderProduct() {
        User user = user(2L);
        Product product = product(1L);
        Order order = finishedOrder(user, product);

        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(userRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(user));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(reviewRepository.existsByUserIdAndOrderIdAndProductIdAndActiveTrue(2L, 7L, 1L)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            review.setId(10L);
            return review;
        });
        when(reviewRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.empty());

        ReviewResponse response = reviewService.create(7L, 1L, 5, " Excelente ", null);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.orderId()).isEqualTo(7L);
        assertThat(response.productId()).isEqualTo(1L);
        assertThat(response.userId()).isEqualTo(2L);
        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.comment()).isEqualTo("Excelente");
        assertThat(response.imageCount()).isZero();
        assertThat(response.active()).isTrue();
    }

    @Test
    void shouldRejectRatingOutsideRange() {
        assertThatThrownBy(() -> reviewService.create(7L, 1L, 6, "Comentário", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Nota deve estar entre 1 e 5");
    }

    @Test
    void shouldRejectReviewWhenOrderIsNotFinished() {
        User user = user(2L);
        Product product = product(1L);
        Order order = finishedOrder(user, product);
        order.setStatus(OrderStatus.DELIVERED);

        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(userRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(user));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> reviewService.create(7L, 1L, 5, "Comentário", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Só é possível avaliar produtos de compras finalizadas");
    }

    @Test
    void shouldRejectReviewForAnotherUserOrder() {
        User owner = user(2L);
        Product product = product(1L);
        Order order = finishedOrder(owner, product);

        when(currentUserService.getCurrentUserId()).thenReturn(99L);
        when(userRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.of(user(99L)));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> reviewService.create(7L, 1L, 5, "Comentário", null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Você não tem permissão para avaliar este pedido");
    }

    @Test
    void shouldRejectDuplicateReview() {
        User user = user(2L);
        Product product = product(1L);
        Order order = finishedOrder(user, product);

        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(userRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(user));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(reviewRepository.existsByUserIdAndOrderIdAndProductIdAndActiveTrue(2L, 7L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.create(7L, 1L, 5, "Comentário", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Você já avaliou este produto neste pedido");

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void shouldRejectMoreThanFiveImages() {
        List<MultipartFile> images = List.of(
                image("1.png"), image("2.png"), image("3.png"),
                image("4.png"), image("5.png"), image("6.png")
        );

        assertThatThrownBy(() -> reviewService.create(7L, 1L, 5, "Comentário", images))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("É permitido anexar no máximo 5 imagens");
    }

    @Test
    void shouldDeleteOwnReview() {
        Review review = Review.builder()
                .id(10L)
                .user(user(2L))
                .product(product(1L))
                .order(finishedOrder(user(2L), product(1L)))
                .rating(5)
                .comment("Comentário")
                .images(new ArrayList<>())
                .active(true)
                .build();

        when(reviewRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(review));
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserId()).thenReturn(2L);

        reviewService.delete(10L);

        assertThat(review.getActive()).isFalse();
        verify(reviewRepository).save(review);
    }

    private MockMultipartFile image(String filename) {
        return new MockMultipartFile("images", filename, "image/png", new byte[]{1, 2, 3});
    }

    private User user(Long id) {
        return User.builder()
                .id(id)
                .fullName("Ana Souza")
                .email("ana@email.com")
                .passwordHash("hash")
                .role(UserRole.CUSTOMER)
                .active(true)
                .build();
    }

    private Product product(Long id) {
        return Product.builder()
                .id(id)
                .name("Teclado Mecânico")
                .description("Descrição")
                .categoryGroup("Periféricos")
                .category("Teclados")
                .brand("PointClick")
                .price(new BigDecimal("100.00"))
                .stockQuantity(10)
                .reservedQuantity(0)
                .active(true)
                .build();
    }

    private Order finishedOrder(User user, Product product) {
        Order order = Order.builder()
                .id(7L)
                .user(user)
                .status(OrderStatus.FINISHED)
                .items(new ArrayList<>())
                .build();

        OrderItem item = OrderItem.builder()
                .id(20L)
                .order(order)
                .product(product)
                .quantity(1)
                .unitPriceAtMoment(product.getPrice())
                .subtotal(product.getPrice())
                .build();

        order.getItems().add(item);
        return order;
    }

    @Test
    void shouldCreateReviewWithImagesIgnoringEmptyFilesAndSavingDisplayOrder() {
        User user = mutationUser(2L);
        Product product = mutationProduct(1L);
        Order order = mutationFinishedOrder(user, product);
        MockMultipartFile first = mutationImage("first.png");
        MockMultipartFile empty = new MockMultipartFile("images", "empty.png", "image/png", new byte[]{});
        MockMultipartFile second = mutationImage("second.png");
        List<MultipartFile> images = List.of(first, empty, second);

        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(userRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(user));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(reviewRepository.existsByUserIdAndOrderIdAndProductIdAndActiveTrue(2L, 7L, 1L)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            review.setId(10L);
            return review;
        });
        when(fileStorageService.storeReviewImage(first)).thenReturn("/uploads/reviews/first.png");
        when(fileStorageService.storeReviewImage(second)).thenReturn("/uploads/reviews/second.png");
        when(reviewRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.empty());

        reviewService.create(7L, 1L, 5, " Excelente ", images);

        ArgumentCaptor<ReviewImage> captor = ArgumentCaptor.forClass(ReviewImage.class);
        verify(reviewImageRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(ReviewImage::getImageUrl)
                .containsExactly("/uploads/reviews/first.png", "/uploads/reviews/second.png");
        assertThat(captor.getAllValues()).extracting(ReviewImage::getDisplayOrder)
                .containsExactly(0, 1);
    }

    @Test
    void shouldRejectProductThatDoesNotBelongToOrder() {
        User user = mutationUser(2L);
        Product product = mutationProduct(1L);
        Order order = mutationFinishedOrder(user, product);

        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(userRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(user));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> reviewService.create(7L, 99L, 5, "Comentário", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Este produto não pertence ao pedido informado");

        verify(productRepository, never()).findById(any());
    }

    @Test
    void shouldRejectWhenProductDoesNotExistDuringCreation() {
        User user = mutationUser(2L);
        Product product = mutationProduct(1L);
        Order order = mutationFinishedOrder(user, product);

        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(userRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(user));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.create(7L, 1L, 5, "Comentário", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Produto não encontrado");
    }

    @Test
    void shouldFindProductReviewsUsingFilters() {
        Product product = mutationProduct(1L);
        Review review = mutationReview(10L, mutationUser(2L), product, mutationFinishedOrder(mutationUser(2L), product), 5, "Ótimo");
        when(productRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(product));
        when(reviewRepository.searchByProduct(1L, 5, true, true)).thenReturn(List.of(review));

        var response = reviewService.findByProductId(1L, 5, true, true);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().rating()).isEqualTo(5);
        assertThat(response.getFirst().comment()).isEqualTo("Ótimo");
    }

    @Test
    void shouldBuildReviewSummaryForProduct() {
        Product product = mutationProduct(1L);
        when(productRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(product));
        when(reviewRepository.averageRatingByProductId(1L)).thenReturn(4.25);
        when(reviewRepository.countByProductIdAndActiveTrue(1L)).thenReturn(8L);
        when(reviewRepository.countByProductIdAndRating(1L, 5)).thenReturn(4L);
        when(reviewRepository.countByProductIdAndRating(1L, 4)).thenReturn(2L);
        when(reviewRepository.countByProductIdAndRating(1L, 3)).thenReturn(1L);
        when(reviewRepository.countByProductIdAndRating(1L, 2)).thenReturn(1L);
        when(reviewRepository.countByProductIdAndRating(1L, 1)).thenReturn(0L);
        when(reviewRepository.countWithCommentsByProductId(1L)).thenReturn(6L);
        when(reviewRepository.countWithMediaByProductId(1L)).thenReturn(3L);

        var response = reviewService.getSummaryByProductId(1L);

        assertThat(response.averageRating()).isEqualTo(4.25);
        assertThat(response.totalReviews()).isEqualTo(8L);
        assertThat(response.fiveStars()).isEqualTo(4L);
        assertThat(response.withComments()).isEqualTo(6L);
        assertThat(response.withMedia()).isEqualTo(3L);
    }

    @Test
    void shouldFindCurrentUserReviews() {
        User user = mutationUser(2L);
        Product product = mutationProduct(1L);
        Review review = mutationReview(10L, user, product, mutationFinishedOrder(user, product), 4, "Bom");
        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(reviewRepository.findByUserIdAndActiveTrueOrderByCreatedAtDesc(2L)).thenReturn(List.of(review));

        var response = reviewService.findMyReviews();

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().userId()).isEqualTo(2L);
        assertThat(response.getFirst().rating()).isEqualTo(4);
    }

    @Test
    void shouldDeleteReviewAsAdminWithoutCheckingOwner() {
        Review review = mutationReview(10L, mutationUser(2L), mutationProduct(1L), mutationFinishedOrder(mutationUser(2L), mutationProduct(1L)), 5, "Comentário");
        when(reviewRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(review));
        when(currentUserService.isAdmin()).thenReturn(true);

        reviewService.delete(10L);

        assertThat(review.getActive()).isFalse();
        verify(reviewRepository).save(review);
    }

    @Test
    void shouldRejectDeleteReviewFromAnotherUser() {
        Review review = mutationReview(10L, mutationUser(2L), mutationProduct(1L), mutationFinishedOrder(mutationUser(2L), mutationProduct(1L)), 5, "Comentário");
        when(reviewRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(review));
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserId()).thenReturn(99L);

        assertThatThrownBy(() -> reviewService.delete(10L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Você não tem permissão para remover esta avaliação");

        verify(reviewRepository, never()).save(any());
    }

    private MockMultipartFile mutationImage(String filename) {
        return new MockMultipartFile("images", filename, "image/png", new byte[]{1, 2, 3});
    }

    private Review mutationReview(Long id, User user, Product product, Order order, Integer rating, String comment) {
        return Review.builder()
                .id(id)
                .user(user)
                .product(product)
                .order(order)
                .rating(rating)
                .comment(comment)
                .images(new ArrayList<>())
                .active(true)
                .build();
    }

    private User mutationUser(Long id) {
        return User.builder()
                .id(id)
                .fullName("Ana Souza")
                .email("ana@email.com")
                .passwordHash("hash")
                .role(UserRole.CUSTOMER)
                .active(true)
                .build();
    }

    private Product mutationProduct(Long id) {
        return Product.builder()
                .id(id)
                .name("Teclado Mecânico")
                .description("Descrição")
                .categoryGroup("Periféricos")
                .category("Teclados")
                .brand("PointClick")
                .price(new BigDecimal("100.00"))
                .stockQuantity(10)
                .reservedQuantity(0)
                .active(true)
                .build();
    }

    private Order mutationFinishedOrder(User user, Product product) {
        Order order = Order.builder()
                .id(7L)
                .user(user)
                .status(OrderStatus.FINISHED)
                .items(new ArrayList<>())
                .build();

        OrderItem item = OrderItem.builder()
                .id(20L)
                .order(order)
                .product(product)
                .quantity(1)
                .unitPriceAtMoment(product.getPrice())
                .subtotal(product.getPrice())
                .build();

        order.getItems().add(item);
        return order;
    }
}
