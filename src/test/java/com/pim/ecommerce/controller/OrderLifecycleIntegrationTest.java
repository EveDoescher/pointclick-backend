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
import com.pim.ecommerce.domain.repository.ProductRepository;
import com.pim.ecommerce.domain.repository.UserRepository;
import com.pim.ecommerce.service.FreightService;
import com.pim.ecommerce.support.AbstractIntegrationTest;
import com.pim.ecommerce.support.TestDatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderLifecycleIntegrationTest extends AbstractIntegrationTest {

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
    private NotificationRepository notificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private FreightService freightService;

    private User customer;
    private Product product;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        reset(freightService);
        when(freightService.calculateFreightByDestinationCep(anyString()))
                .thenReturn(new BigDecimal("15.00"));

        createUserWithAddress("Admin PointClick", "admin@pointclick.com", "123456", UserRole.ADMIN);
        customer = createUserWithAddress("Ana Souza", "ana@email.com", "123456", UserRole.CUSTOMER);
        product = productRepository.save(product("Teclado Mecânico", new BigDecimal("399.90"), 5, 0));
    }

    @Test
    void shouldShipPaidOrderAsAdmin() throws Exception {
        String customerToken = loginAndExtractToken("ana@email.com", "123456");
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");
        Long orderId = createPaidOrder(customerToken);

        mockMvc.perform(post("/orders/{orderId}/ship", orderId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.shippedAt", not(blankOrNullString())));

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.getShippedAt()).isNotNull();
        assertThat(notificationRepository.count()).isEqualTo(2);
    }

    @Test
    void shouldDeliverShippedOrderAsAdmin() throws Exception {
        String customerToken = loginAndExtractToken("ana@email.com", "123456");
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");
        Long orderId = createPaidOrder(customerToken);
        shipOrder(adminToken, orderId);

        mockMvc.perform(post("/orders/{orderId}/deliver", orderId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveredAt", not(blankOrNullString())));

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getDeliveredAt()).isNotNull();
    }

    @Test
    void shouldFinishDeliveredOrderWhenCustomerConfirmsDelivery() throws Exception {
        String customerToken = loginAndExtractToken("ana@email.com", "123456");
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");
        Long orderId = createPaidOrder(customerToken);
        shipOrder(adminToken, orderId);
        deliverOrder(adminToken, orderId);

        mockMvc.perform(post("/orders/{orderId}/confirm-delivery", orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.finishedAt", not(blankOrNullString())));

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FINISHED);
        assertThat(order.getFinishedAt()).isNotNull();
    }

    @Test
    void shouldRejectCancelAfterPayment() throws Exception {
        String customerToken = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createPaidOrder(customerToken);

        mockMvc.perform(post("/orders/{orderId}/cancel", orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.detail").value("Não é possível cancelar pedidos após o pagamento"));

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void shouldRejectShipOrderWhenItIsNotPaid() throws Exception {
        String customerToken = loginAndExtractToken("ana@email.com", "123456");
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");
        Long orderId = createClosedOrder(customerToken);

        mockMvc.perform(post("/orders/{orderId}/ship", orderId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.detail").value("Só é possível enviar pedidos com status PAID"));

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CLOSED);
    }

    @Test
    void shouldReopenExpiredClosedOrderAsAdminAndReleaseReservation() throws Exception {
        String customerToken = loginAndExtractToken("ana@email.com", "123456");
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");
        Long orderId = createClosedOrder(customerToken);

        Order closedOrder = orderRepository.findById(orderId).orElseThrow();
        closedOrder.setReservationExpiresAt(LocalDateTime.now().minusHours(1));
        orderRepository.save(closedOrder);

        mockMvc.perform(post("/orders/{orderId}/reopen-expired", orderId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.freightAmount").value(0))
                .andExpect(jsonPath("$.reservationExpiresAt").isEmpty())
                .andExpect(jsonPath("$.deliveryAddress").isEmpty());

        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        Order reopenedOrder = orderRepository.findById(orderId).orElseThrow();

        assertThat(updatedProduct.getReservedQuantity()).isZero();
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(5);
        assertThat(reopenedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(reopenedOrder.getReservationExpiresAt()).isNull();
    }

    private Long createPaidOrder(String customerToken) throws Exception {
        Long orderId = createClosedOrder(customerToken);

        mockMvc.perform(post("/payments/orders/{orderId}", orderId)
                        .header("Authorization", "Bearer " + customerToken)
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
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        return orderId;
    }

    private Long createClosedOrder(String token) throws Exception {
        Long orderId = createOrderAndExtractId(token, customer.getId());
        addItem(token, orderId, product.getId(), 2);

        mockMvc.perform(post("/orders/{orderId}/close", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        return orderId;
    }

    private void shipOrder(String adminToken, Long orderId) throws Exception {
        mockMvc.perform(post("/orders/{orderId}/ship", orderId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    private void deliverOrder(String adminToken, Long orderId) throws Exception {
        mockMvc.perform(post("/orders/{orderId}/deliver", orderId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
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

    private Product product(String name, BigDecimal price, Integer stockQuantity, Integer reservedQuantity) {
        return Product.builder()
                .name(name)
                .description("Descrição do produto " + name)
                .categoryGroup("Periféricos")
                .category("Teclados")
                .brand("PointClick")
                .model("Modelo Teste")
                .price(price)
                .stockQuantity(stockQuantity)
                .reservedQuantity(reservedQuantity)
                .imageUrl("/uploads/products/teste.png")
                .active(true)
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
}
