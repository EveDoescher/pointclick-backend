package com.pim.ecommerce.controller;

import com.jayway.jsonpath.JsonPath;
import com.pim.ecommerce.domain.entity.Address;
import com.pim.ecommerce.domain.entity.Order;
import com.pim.ecommerce.domain.entity.Product;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.entity.enums.OrderStatus;
import com.pim.ecommerce.domain.entity.enums.UserRole;
import com.pim.ecommerce.domain.repository.NotificationRepository;
import com.pim.ecommerce.domain.repository.OrderRepository;
import com.pim.ecommerce.domain.repository.PaymentRepository;
import com.pim.ecommerce.domain.repository.ProductRepository;
import com.pim.ecommerce.domain.repository.RefreshTokenRepository;
import com.pim.ecommerce.domain.repository.UserRepository;
import com.pim.ecommerce.service.FreightService;
import com.pim.ecommerce.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CheckoutPaymentIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private FreightService freightService;

    private User customer;
    private Product product;

    @BeforeEach
    void setUp() {
        reset(freightService);

        paymentRepository.deleteAll();
        notificationRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        customer = createUserWithAddress(
                "Ana Souza",
                "ana@email.com",
                "123456",
                UserRole.CUSTOMER
        );

        product = productRepository.save(product(
                "Teclado Mecânico",
                "Periféricos",
                "Teclados",
                "PointClick",
                BigDecimal.valueOf(399.90),
                5,
                0,
                true
        ));
    }

    @Test
    void shouldCloseOrderAndReserveStock() throws Exception {
        when(freightService.calculateFreightByDestinationCep(anyString()))
                .thenReturn(BigDecimal.valueOf(15.00));

        String token = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createOrderAndExtractId(token, customer.getId());

        addItem(token, orderId, product.getId(), 2);

        mockMvc.perform(post("/orders/{orderId}/close", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.itemsAmount").value(799.80))
                .andExpect(jsonPath("$.freightAmount").value(15.00))
                .andExpect(jsonPath("$.totalAmount").value(814.80))
                .andExpect(jsonPath("$.deliveryAddress", not(blankOrNullString())))
                .andExpect(jsonPath("$.closedAt", not(blankOrNullString())))
                .andExpect(jsonPath("$.reservationExpiresAt", not(blankOrNullString())));

        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();

        org.assertj.core.api.Assertions.assertThat(updatedProduct.getStockQuantity()).isEqualTo(5);
        org.assertj.core.api.Assertions.assertThat(updatedProduct.getReservedQuantity()).isEqualTo(2);

        Order closedOrder = orderRepository.findById(orderId).orElseThrow();

        org.assertj.core.api.Assertions.assertThat(closedOrder.getStatus()).isEqualTo(OrderStatus.CLOSED);
        org.assertj.core.api.Assertions.assertThat(closedOrder.getFreightAmount()).isEqualByComparingTo("15.00");
        org.assertj.core.api.Assertions.assertThat(closedOrder.getReservationExpiresAt()).isNotNull();

        verify(freightService).calculateFreightByDestinationCep("13480370");
    }

    @Test
    void shouldApproveCreditCardPaymentAndDecreaseStock() throws Exception {
        when(freightService.calculateFreightByDestinationCep(anyString()))
                .thenReturn(BigDecimal.valueOf(15.00));

        String token = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createOrderAndExtractId(token, customer.getId());

        addItem(token, orderId, product.getId(), 2);
        closeOrder(token, orderId);

        mockMvc.perform(post("/payments/orders/{orderId}", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentMethod": "CREDIT_CARD",
                                  "cardNumber": "4111 1111 1111 1111",
                                  "cardHolderName": "ANA SOUZA",
                                  "expirationDate": "12/30",
                                  "cvv": "123",
                                  "installments": 3,
                                  "notes": "Entregar em horário comercial"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", not(blankOrNullString())))
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.method").value("CREDIT_CARD"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.amount").value(814.80))
                .andExpect(jsonPath("$.installments").value(3))
                .andExpect(jsonPath("$.cardLastFourDigits").value("1111"))
                .andExpect(jsonPath("$.notes").value("Entregar em horário comercial"))
                .andExpect(jsonPath("$.confirmedAt", not(blankOrNullString())));

        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();

        org.assertj.core.api.Assertions.assertThat(updatedProduct.getStockQuantity()).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(updatedProduct.getReservedQuantity()).isZero();

        Order paidOrder = orderRepository.findById(orderId).orElseThrow();

        org.assertj.core.api.Assertions.assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        org.assertj.core.api.Assertions.assertThat(paidOrder.getPaymentMethod().name()).isEqualTo("CREDIT_CARD");
        org.assertj.core.api.Assertions.assertThat(paidOrder.getPaidAt()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(paidOrder.getReservationExpiresAt()).isNull();
        org.assertj.core.api.Assertions.assertThat(paidOrder.getNotes()).isEqualTo("Entregar em horário comercial");

        org.assertj.core.api.Assertions.assertThat(paymentRepository.count()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldRejectPaymentWhenOrderIsStillPending() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createOrderAndExtractId(token, customer.getId());

        addItem(token, orderId, product.getId(), 2);

        mockMvc.perform(post("/payments/orders/{orderId}", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentMethod": "CREDIT_CARD",
                                  "cardNumber": "4111 1111 1111 1111",
                                  "cardHolderName": "ANA SOUZA",
                                  "expirationDate": "12/30",
                                  "cvv": "123",
                                  "installments": 1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.detail").value("Só é possível pagar pedidos com status CLOSED"))
                .andExpect(jsonPath("$.instance").value("/payments/orders/" + orderId));

        org.assertj.core.api.Assertions.assertThat(paymentRepository.count()).isZero();

        Product unchangedProduct = productRepository.findById(product.getId()).orElseThrow();

        org.assertj.core.api.Assertions.assertThat(unchangedProduct.getStockQuantity()).isEqualTo(5);
        org.assertj.core.api.Assertions.assertThat(unchangedProduct.getReservedQuantity()).isZero();
    }

    @Test
    void shouldRejectCreditCardWithInvalidExpirationMonth() throws Exception {
        when(freightService.calculateFreightByDestinationCep(anyString()))
                .thenReturn(BigDecimal.valueOf(15.00));

        String token = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createOrderAndExtractId(token, customer.getId());

        addItem(token, orderId, product.getId(), 2);
        closeOrder(token, orderId);

        mockMvc.perform(post("/payments/orders/{orderId}", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentMethod": "CREDIT_CARD",
                                  "cardNumber": "4111 1111 1111 1111",
                                  "cardHolderName": "ANA SOUZA",
                                  "expirationDate": "13/30",
                                  "cvv": "123",
                                  "installments": 1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.detail").value("Data de expiração inválida"))
                .andExpect(jsonPath("$.instance").value("/payments/orders/" + orderId));

        Order order = orderRepository.findById(orderId).orElseThrow();
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();

        org.assertj.core.api.Assertions.assertThat(order.getStatus()).isEqualTo(OrderStatus.CLOSED);
        org.assertj.core.api.Assertions.assertThat(updatedProduct.getStockQuantity()).isEqualTo(5);
        org.assertj.core.api.Assertions.assertThat(updatedProduct.getReservedQuantity()).isEqualTo(2);
    }

    private User createUserWithAddress(String fullName, String email, String password, UserRole role) {
        User user = User.builder()
                .fullName(fullName)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(role)
                .active(true)
                .address(Address.builder()
                        .cep("13480-370")
                        .street("Rua das Flores")
                        .number("123")
                        .complement("Casa")
                        .city("Limeira")
                        .state("SP")
                        .build())
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

    private void closeOrder(String token, Long orderId) throws Exception {
        mockMvc.perform(post("/orders/{orderId}/close", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }
}