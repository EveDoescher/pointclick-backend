package com.pim.ecommerce.controller;

import com.jayway.jsonpath.JsonPath;
import com.pim.ecommerce.domain.entity.Order;
import com.pim.ecommerce.domain.entity.OrderItem;
import com.pim.ecommerce.domain.entity.Product;
import com.pim.ecommerce.domain.entity.Review;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.entity.enums.OrderStatus;
import com.pim.ecommerce.domain.entity.enums.UserRole;
import com.pim.ecommerce.domain.repository.OrderRepository;
import com.pim.ecommerce.domain.repository.ProductRepository;
import com.pim.ecommerce.domain.repository.ReviewRepository;
import com.pim.ecommerce.domain.repository.UserRepository;
import com.pim.ecommerce.support.AbstractIntegrationTest;
import com.pim.ecommerce.support.TestDatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User customer;
    private User otherCustomer;
    private Product product;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();

        customer = createUser("Ana Souza", "ana@email.com", "123456", UserRole.CUSTOMER);
        otherCustomer = createUser("Outro Cliente", "outro@email.com", "123456", UserRole.CUSTOMER);
        product = productRepository.save(product("Teclado Mecânico", new BigDecimal("399.90")));
    }

    @Test
    void shouldCreateReviewForFinishedOrder() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        Order order = createOrder(customer, product, OrderStatus.FINISHED);

        mockMvc.perform(multipart("/orders/{orderId}/products/{productId}/reviews", order.getId(), product.getId())
                        .param("rating", "5")
                        .param("comment", "Produto excelente")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", not(blankOrNullString())))
                .andExpect(jsonPath("$.orderId").value(order.getId()))
                .andExpect(jsonPath("$.productId").value(product.getId()))
                .andExpect(jsonPath("$.productName").value("Teclado Mecânico"))
                .andExpect(jsonPath("$.userId").value(customer.getId()))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Produto excelente"))
                .andExpect(jsonPath("$.active").value(true));

        assertThat(reviewRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldRejectReviewWhenOrderIsNotFinished() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        Order order = createOrder(customer, product, OrderStatus.DELIVERED);

        mockMvc.perform(multipart("/orders/{orderId}/products/{productId}/reviews", order.getId(), product.getId())
                        .param("rating", "5")
                        .param("comment", "Produto excelente")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.detail").value("Só é possível avaliar produtos de compras finalizadas"));

        assertThat(reviewRepository.count()).isZero();
    }

    @Test
    void shouldRejectReviewForAnotherUserOrder() throws Exception {
        String otherToken = loginAndExtractToken("outro@email.com", "123456");
        Order order = createOrder(customer, product, OrderStatus.FINISHED);

        mockMvc.perform(multipart("/orders/{orderId}/products/{productId}/reviews", order.getId(), product.getId())
                        .param("rating", "5")
                        .param("comment", "Produto excelente")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Acesso negado"))
                .andExpect(jsonPath("$.detail").value("Você não tem permissão para acessar este recurso."));
    }

    @Test
    void shouldRejectDuplicateReviewForSameOrderAndProduct() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        Order order = createOrder(customer, product, OrderStatus.FINISHED);

        createReview(customer, order, product, 5, "Primeira avaliação");

        mockMvc.perform(multipart("/orders/{orderId}/products/{productId}/reviews", order.getId(), product.getId())
                        .param("rating", "4")
                        .param("comment", "Segunda avaliação")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Você já avaliou este produto neste pedido"));

        assertThat(reviewRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldListProductReviewsAndSummary() throws Exception {
        Order firstOrder = createOrder(customer, product, OrderStatus.FINISHED);
        Order secondOrder = createOrder(otherCustomer, product, OrderStatus.FINISHED);

        createReview(customer, firstOrder, product, 5, "Excelente");
        createReview(otherCustomer, secondOrder, product, 3, "Ok");

        mockMvc.perform(get("/products/{productId}/reviews", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/products/{productId}/reviews/summary", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(4.0))
                .andExpect(jsonPath("$.totalReviews").value(2))
                .andExpect(jsonPath("$.fiveStars").value(1))
                .andExpect(jsonPath("$.threeStars").value(1))
                .andExpect(jsonPath("$.withComments").value(2));
    }

    @Test
    void shouldDeleteOwnReviewLogically() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        Order order = createOrder(customer, product, OrderStatus.FINISHED);
        Review review = createReview(customer, order, product, 5, "Excelente");

        mockMvc.perform(delete("/reviews/{reviewId}", review.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        Review deletedReview = reviewRepository.findById(review.getId()).orElseThrow();
        assertThat(deletedReview.getActive()).isFalse();
    }

    private User createUser(String fullName, String email, String password, UserRole role) {
        return userRepository.save(User.builder()
                .fullName(fullName)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(role)
                .active(true)
                .build());
    }

    private Product product(String name, BigDecimal price) {
        return Product.builder()
                .name(name)
                .description("Descrição do produto " + name)
                .categoryGroup("Periféricos")
                .category("Teclados")
                .brand("PointClick")
                .model("Modelo Teste")
                .price(price)
                .stockQuantity(10)
                .reservedQuantity(0)
                .imageUrl("/uploads/products/teste.png")
                .active(true)
                .build();
    }

    private Order createOrder(User user, Product product, OrderStatus status) {
        Order order = Order.builder()
                .user(user)
                .status(status)
                .itemsAmount(product.getPrice())
                .discountAmount(BigDecimal.ZERO)
                .freightAmount(BigDecimal.ZERO)
                .totalAmount(product.getPrice())
                .build();

        OrderItem item = OrderItem.builder()
                .order(order)
                .product(product)
                .quantity(1)
                .unitPriceAtMoment(product.getPrice())
                .subtotal(product.getPrice())
                .build();

        order.getItems().add(item);

        return orderRepository.save(order);
    }

    private Review createReview(User user, Order order, Product product, Integer rating, String comment) {
        return reviewRepository.save(Review.builder()
                .user(user)
                .order(order)
                .product(product)
                .rating(rating)
                .comment(comment)
                .active(true)
                .build());
    }

    private String loginAndExtractToken(String email, String password) throws Exception {
        String responseBody = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(responseBody, "$.token");
    }
}
