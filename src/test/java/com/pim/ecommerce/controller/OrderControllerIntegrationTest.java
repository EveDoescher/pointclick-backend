package com.pim.ecommerce.controller;

import com.jayway.jsonpath.JsonPath;
import com.pim.ecommerce.domain.entity.Address;
import com.pim.ecommerce.domain.entity.Order;
import com.pim.ecommerce.domain.entity.Product;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.entity.enums.OrderStatus;
import com.pim.ecommerce.domain.entity.enums.UserRole;
import com.pim.ecommerce.domain.repository.OrderRepository;
import com.pim.ecommerce.domain.repository.ProductRepository;
import com.pim.ecommerce.domain.repository.UserRepository;
import com.pim.ecommerce.integration.viacep.ViaCepClient;
import com.pim.ecommerce.integration.viacep.ViaCepResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerIntegrationTest extends AbstractIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private FreightService freightService;

    @MockitoBean
    private ViaCepClient viaCepClient;

    private User admin;
    private User customer;
    private User otherCustomer;
    private Product product;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        reset(freightService, viaCepClient);

        when(freightService.calculateFreightByDestinationCep(anyString()))
                .thenReturn(new BigDecimal("15.00"));
        when(viaCepClient.getAddressByCep(anyString()))
                .thenReturn(new ViaCepResponse(
                        "13480-370",
                        "Rua das Flores",
                        "",
                        "Centro",
                        "Limeira",
                        "SP",
                        false
                ));

        admin = createUserWithAddress("Admin PointClick", "admin@pointclick.com", "123456", UserRole.ADMIN);
        customer = createUserWithAddress("Ana Souza", "ana@email.com", "123456", UserRole.CUSTOMER);
        otherCustomer = createUserWithAddress("Outro Cliente", "outro@email.com", "123456", UserRole.CUSTOMER);
        product = productRepository.save(product("Teclado Mecânico", new BigDecimal("399.90"), 5, 0));
    }

    @Test
    void shouldCreateOrderForCurrentUser() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": %d
                                }
                                """.formatted(customer.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", not(blankOrNullString())))
                .andExpect(jsonPath("$.userId").value(customer.getId()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void shouldRejectCreateOrderForAnotherUserAsCustomer() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": %d
                                }
                                """.formatted(otherCustomer.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void shouldReturnNoContentWhenCurrentCartDoesNotExist() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");

        mockMvc.perform(get("/orders/current")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldFindCurrentCart() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createOrderAndExtractId(token, customer.getId());

        mockMvc.perform(get("/orders/current")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void shouldAddUpdateAndRemoveItemFromPendingOrder() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createOrderAndExtractId(token, customer.getId());
        Long itemId = addItemAndExtractItemId(token, orderId, product.getId(), 2);

        mockMvc.perform(put("/orders/{orderId}/items/{itemId}", orderId, itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.itemsAmount").value(1199.70));

        mockMvc.perform(delete("/orders/{orderId}/items/{itemId}", orderId, itemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalAmount").value(0));
    }

    @Test
    void shouldRejectAddItemAboveAvailableStock() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createOrderAndExtractId(token, customer.getId());

        mockMvc.perform(post("/orders/{orderId}/items", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "quantity": 6
                                }
                                """.formatted(product.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Estoque insuficiente para o produto: Teclado Mecânico"));
    }

    @Test
    void shouldFindOrderByIdAndRejectAnotherUserOrder() throws Exception {
        String customerToken = loginAndExtractToken("ana@email.com", "123456");
        String otherCustomerToken = loginAndExtractToken("outro@email.com", "123456");
        Long orderId = createOrderAndExtractId(customerToken, customer.getId());

        mockMvc.perform(get("/orders/{orderId}", orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));

        mockMvc.perform(get("/orders/{orderId}", orderId)
                        .header("Authorization", "Bearer " + otherCustomerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void shouldListMyOrders() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createOrderAndExtractId(token, customer.getId());

        mockMvc.perform(get("/orders/my")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(orderId))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void shouldListOrdersForAdminAndFindByIdForAdmin() throws Exception {
        String customerToken = loginAndExtractToken("ana@email.com", "123456");
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");
        Long orderId = createOrderAndExtractId(customerToken, customer.getId());

        mockMvc.perform(get("/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(orderId))
                .andExpect(jsonPath("$[0].customerEmail").value("ana@email.com"));

        mockMvc.perform(get("/orders/admin/{orderId}", orderId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));
    }

    @Test
    void shouldReturnOrderStatusSummaryForAdmin() throws Exception {
        String customerToken = loginAndExtractToken("ana@email.com", "123456");
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");
        createOrderAndExtractId(customerToken, customer.getId());
        orderRepository.save(orderWithStatus(customer, OrderStatus.CLOSED));

        mockMvc.perform(get("/orders/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(1))
                .andExpect(jsonPath("$.closed").value(1));
    }

    @Test
    void shouldQuoteShippingForPendingOrder() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createOrderAndExtractId(token, customer.getId());

        mockMvc.perform(post("/orders/{orderId}/shipping/quote", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cep": "13480-370"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cep").value("13480370"))
                .andExpect(jsonPath("$.shippingPrice").value(15.00))
                .andExpect(jsonPath("$.estimatedDays").value(5))
                .andExpect(jsonPath("$.city").value("Limeira"))
                .andExpect(jsonPath("$.state").value("SP"));
    }

    @Test
    void shouldCloseAndCancelOrder() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createOrderAndExtractId(token, customer.getId());
        addItemAndExtractItemId(token, orderId, product.getId(), 2);

        mockMvc.perform(post("/orders/{orderId}/close", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.freightAmount").value(15.00))
                .andExpect(jsonPath("$.reservationExpiresAt", not(blankOrNullString())));

        mockMvc.perform(post("/orders/{orderId}/cancel", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(productRepository.findById(product.getId()).orElseThrow().getReservedQuantity()).isZero();
    }

    private Order orderWithStatus(User user, OrderStatus status) {
        return Order.builder()
                .user(user)
                .status(status)
                .itemsAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .freightAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .build();
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

    private Long addItemAndExtractItemId(String token, Long orderId, Long productId, Integer quantity) throws Exception {
        String responseBody = mockMvc.perform(post("/orders/{orderId}/items", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "quantity": %d
                                }
                                """.formatted(productId, quantity)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number itemId = JsonPath.read(responseBody, "$.items[0].id");
        return itemId.longValue();
    }
}
