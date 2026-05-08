package com.pim.ecommerce.controller;

import com.jayway.jsonpath.JsonPath;
import com.pim.ecommerce.domain.entity.Coupon;
import com.pim.ecommerce.domain.entity.Product;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.entity.enums.DiscountType;
import com.pim.ecommerce.domain.entity.enums.UserRole;
import com.pim.ecommerce.domain.repository.CouponRepository;
import com.pim.ecommerce.domain.repository.OrderRepository;
import com.pim.ecommerce.domain.repository.ProductRepository;
import com.pim.ecommerce.domain.repository.RefreshTokenRepository;
import com.pim.ecommerce.domain.repository.UserRepository;
import com.pim.ecommerce.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CouponOrderIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User customer;
    private Product product;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        couponRepository.deleteAll();
        productRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        createUser("Admin PointClick", "admin@pointclick.com", "123456", UserRole.ADMIN);
        customer = createUser("Ana Souza", "ana@email.com", "123456", UserRole.CUSTOMER);

        product = productRepository.save(product(
                "Teclado Mecânico",
                "Periféricos",
                "Teclados",
                "PointClick",
                new BigDecimal("399.90"),
                10,
                0,
                true
        ));
    }

    @Test
    void shouldCreateCouponAsAdmin() throws Exception {
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");

        mockMvc.perform(post("/coupons")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": " point10 ",
                                  "description": "Cupom de 10% para testes",
                                  "discountType": "PERCENTAGE",
                                  "discountValue": 10.00,
                                  "minimumOrderValue": 100.00,
                                  "active": true,
                                  "usageLimit": 50
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", not(blankOrNullString())))
                .andExpect(jsonPath("$.code").value("POINT10"))
                .andExpect(jsonPath("$.description").value("Cupom de 10% para testes"))
                .andExpect(jsonPath("$.discountType").value("PERCENTAGE"))
                .andExpect(jsonPath("$.discountValue").value(10.00))
                .andExpect(jsonPath("$.minimumOrderValue").value(100.00))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.usageLimit").value(50))
                .andExpect(jsonPath("$.usedCount").value(0))
                .andExpect(jsonPath("$.currentlyValid").value(true));

        assertThat(couponRepository.findByCode("POINT10")).isPresent();
    }

    @Test
    void shouldRejectCreateCouponAsCustomer() throws Exception {
        String customerToken = loginAndExtractToken("ana@email.com", "123456");

        mockMvc.perform(post("/coupons")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "POINT10",
                                  "description": "Cupom de 10% para testes",
                                  "discountType": "PERCENTAGE",
                                  "discountValue": 10.00,
                                  "minimumOrderValue": 100.00,
                                  "active": true
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Acesso negado"))
                .andExpect(jsonPath("$.detail").value("Você não tem permissão para acessar este recurso."))
                .andExpect(jsonPath("$.instance").value("/coupons"));

        assertThat(couponRepository.count()).isZero();
    }

    @Test
    void shouldApplyPercentageCouponToPendingOrder() throws Exception {
        createCoupon(
                "POINT10",
                DiscountType.PERCENTAGE,
                new BigDecimal("10.00"),
                new BigDecimal("100.00"),
                true,
                null,
                null,
                null
        );

        String token = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createOrderAndExtractId(token, customer.getId());

        addItem(token, orderId, product.getId(), 2);

        mockMvc.perform(post("/orders/{orderId}/coupon", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "point10"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.couponCode").value("POINT10"))
                .andExpect(jsonPath("$.itemsAmount").value(799.80))
                .andExpect(jsonPath("$.discountAmount").value(79.98))
                .andExpect(jsonPath("$.freightAmount").value(0))
                .andExpect(jsonPath("$.totalAmount").value(719.82));

        var order = orderRepository.findById(orderId).orElseThrow();

        assertThat(order.getCouponCode()).isEqualTo("POINT10");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("79.98");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("719.82");
    }

    @Test
    void shouldApplyFixedAmountCouponWithoutMakingTotalNegative() throws Exception {
        createCoupon(
                "POINT999",
                DiscountType.FIXED_AMOUNT,
                new BigDecimal("999.00"),
                BigDecimal.ZERO,
                true,
                null,
                null,
                null
        );

        String token = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createOrderAndExtractId(token, customer.getId());

        addItem(token, orderId, product.getId(), 1);

        mockMvc.perform(post("/orders/{orderId}/coupon", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "POINT999"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.couponCode").value("POINT999"))
                .andExpect(jsonPath("$.itemsAmount").value(399.90))
                .andExpect(jsonPath("$.discountAmount").value(399.90))
                .andExpect(jsonPath("$.totalAmount").value(0));

        var order = orderRepository.findById(orderId).orElseThrow();

        assertThat(order.getDiscountAmount()).isEqualByComparingTo("399.90");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldRejectExpiredCouponWhenApplyingToOrder() throws Exception {
        createCoupon(
                "OLD10",
                DiscountType.PERCENTAGE,
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                true,
                LocalDateTime.now().minusDays(10),
                LocalDateTime.now().minusDays(1),
                null
        );

        String token = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createOrderAndExtractId(token, customer.getId());

        addItem(token, orderId, product.getId(), 1);

        mockMvc.perform(post("/orders/{orderId}/coupon", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "OLD10"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.detail").value("Cupom expirado"))
                .andExpect(jsonPath("$.instance").value("/orders/" + orderId + "/coupon"));

        var order = orderRepository.findById(orderId).orElseThrow();

        assertThat(order.getCouponCode()).isNull();
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("0.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("399.90");
    }

    @Test
    void shouldRejectCouponWhenMinimumOrderValueIsNotReached() throws Exception {
        createCoupon(
                "MIN1000",
                DiscountType.PERCENTAGE,
                new BigDecimal("10.00"),
                new BigDecimal("1000.00"),
                true,
                null,
                null,
                null
        );

        String token = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createOrderAndExtractId(token, customer.getId());

        addItem(token, orderId, product.getId(), 1);

        mockMvc.perform(post("/orders/{orderId}/coupon", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "MIN1000"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.detail", containsString("Valor mínimo para este cupom")))
                .andExpect(jsonPath("$.detail", containsString("Adicione mais")))
                .andExpect(jsonPath("$.instance").value("/orders/" + orderId + "/coupon"));

        var order = orderRepository.findById(orderId).orElseThrow();

        assertThat(order.getCouponCode()).isNull();
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("0.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("399.90");
    }

    @Test
    void shouldRemoveCouponFromPendingOrder() throws Exception {
        createCoupon(
                "POINT10",
                DiscountType.PERCENTAGE,
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                true,
                null,
                null,
                null
        );

        String token = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createOrderAndExtractId(token, customer.getId());

        addItem(token, orderId, product.getId(), 2);
        applyCoupon(token, orderId, "POINT10");

        mockMvc.perform(delete("/orders/{orderId}/coupon", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.couponCode").isEmpty())
                .andExpect(jsonPath("$.itemsAmount").value(799.80))
                .andExpect(jsonPath("$.discountAmount").value(0))
                .andExpect(jsonPath("$.totalAmount").value(799.80));

        var order = orderRepository.findById(orderId).orElseThrow();

        assertThat(order.getCoupon()).isNull();
        assertThat(order.getCouponCode()).isNull();
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("0.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("799.80");
    }

    @Test
    void shouldRejectApplyingCouponToOrderWithoutItems() throws Exception {
        createCoupon(
                "POINT10",
                DiscountType.PERCENTAGE,
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                true,
                null,
                null,
                null
        );

        String token = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createOrderAndExtractId(token, customer.getId());

        mockMvc.perform(post("/orders/{orderId}/coupon", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "POINT10"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.detail").value("Adicione itens ao carrinho antes de aplicar cupom"))
                .andExpect(jsonPath("$.instance").value("/orders/" + orderId + "/coupon"));
    }

    private User createUser(String fullName, String email, String password, UserRole role) {
        User user = User.builder()
                .fullName(fullName)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(role)
                .active(true)
                .build();

        return userRepository.save(user);
    }

    private Product product(
            String name,
            String categoryGroup,
            String category,
            String brand,
            BigDecimal price,
            Integer stockQuantity,
            Integer reservedQuantity,
            Boolean active
    ) {
        return Product.builder()
                .name(name)
                .description("Descrição do produto " + name)
                .categoryGroup(categoryGroup)
                .category(category)
                .brand(brand)
                .model("Modelo Teste")
                .price(price)
                .stockQuantity(stockQuantity)
                .reservedQuantity(reservedQuantity)
                .imageUrl("/uploads/products/teste.png")
                .active(active)
                .build();
    }

    private Coupon createCoupon(
            String code,
            DiscountType discountType,
            BigDecimal discountValue,
            BigDecimal minimumOrderValue,
            Boolean active,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            Integer usageLimit
    ) {
        Coupon coupon = Coupon.builder()
                .code(code)
                .description("Cupom de teste")
                .discountType(discountType)
                .discountValue(discountValue)
                .minimumOrderValue(minimumOrderValue)
                .active(active)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .usageLimit(usageLimit)
                .usedCount(0)
                .build();

        return couponRepository.save(coupon);
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

    private Long createOrderAndExtractId(String token, Long userId) throws Exception {
        String responseBody = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": %d
                                }
                                """.formatted(userId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(responseBody, "$.id");
        return id.longValue();
    }

    private void addItem(String token, Long orderId, Long productId, Integer quantity) throws Exception {
        mockMvc.perform(post("/orders/{orderId}/items", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "quantity": %d
                                }
                                """.formatted(productId, quantity)))
                .andExpect(status().isOk());
    }

    private void applyCoupon(String token, Long orderId, String code) throws Exception {
        mockMvc.perform(post("/orders/{orderId}/coupon", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s"
                                }
                                """.formatted(code)))
                .andExpect(status().isOk());
    }
}