package com.pim.ecommerce.controller;

import com.jayway.jsonpath.JsonPath;
import com.pim.ecommerce.domain.entity.Address;
import com.pim.ecommerce.domain.entity.Order;
import com.pim.ecommerce.domain.entity.Product;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.entity.enums.OrderStatus;
import com.pim.ecommerce.domain.entity.enums.PaymentStatus;
import com.pim.ecommerce.domain.entity.enums.UserRole;
import com.pim.ecommerce.domain.repository.NotificationRepository;
import com.pim.ecommerce.domain.repository.OrderRepository;
import com.pim.ecommerce.domain.repository.PaymentRepository;
import com.pim.ecommerce.domain.repository.ProductRepository;
import com.pim.ecommerce.domain.repository.UserRepository;
import com.pim.ecommerce.service.CodeGeneratorService;
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
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentPixBankSlipIntegrationTest extends AbstractIntegrationTest {

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
    private PaymentRepository paymentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private FreightService freightService;

    @MockitoBean
    private CodeGeneratorService codeGeneratorService;

    private User customer;
    private Product product;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        reset(freightService, codeGeneratorService);

        when(freightService.calculateFreightByDestinationCep(anyString()))
                .thenReturn(new BigDecimal("15.00"));
        when(codeGeneratorService.generateQrCodeBase64(anyString()))
                .thenReturn("data:image/png;base64,pix-test");
        when(codeGeneratorService.generateCode128BarCodeBase64(anyString()))
                .thenReturn("data:image/png;base64,boleto-test");

        customer = createUserWithAddress("Ana Souza", "ana@email.com", "123456", UserRole.CUSTOMER);
        product = productRepository.save(product("Teclado Mecânico", new BigDecimal("399.90"), 5, 0));
    }

    @Test
    void shouldCreatePixPaymentAsPendingAndAllowConsultingByOrder() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createClosedOrder(token);

        String responseBody = mockMvc.perform(post("/payments/orders/{orderId}", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentMethod": "PIX"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", not(blankOrNullString())))
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.method").value("PIX"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.pixCode", not(blankOrNullString())))
                .andExpect(jsonPath("$.pixQrCodeBase64").value("data:image/png;base64,pix-test"))
                .andExpect(jsonPath("$.pixConfirmationUrl", not(blankOrNullString())))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number paymentId = JsonPath.read(responseBody, "$.id");

        mockMvc.perform(get("/payments/orders/{orderId}", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.longValue()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        Order order = orderRepository.findById(orderId).orElseThrow();
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CLOSED);
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(5);
        assertThat(updatedProduct.getReservedQuantity()).isEqualTo(2);
    }

    @Test
    void shouldConfirmPixPaymentWithPublicToken() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createClosedOrder(token);
        String paymentBody = createPayment(token, orderId, "PIX");

        Number paymentId = JsonPath.read(paymentBody, "$.id");
        String confirmationUrl = JsonPath.read(paymentBody, "$.pixConfirmationUrl");
        String confirmationToken = extractTokenFromUrl(confirmationUrl);

        mockMvc.perform(post("/payments/{paymentId}/confirm-pix", paymentId.longValue())
                        .param("token", confirmationToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.confirmedAt", not(blankOrNullString())));

        Order paidOrder = orderRepository.findById(orderId).orElseThrow();
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();

        assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paidOrder.getPaymentMethod().name()).isEqualTo("PIX");
        assertThat(paidOrder.getReservationExpiresAt()).isNull();
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(3);
        assertThat(updatedProduct.getReservedQuantity()).isZero();
        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldRejectPixConfirmationWithInvalidToken() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createClosedOrder(token);
        String paymentBody = createPayment(token, orderId, "PIX");

        Number paymentId = JsonPath.read(paymentBody, "$.id");

        mockMvc.perform(post("/payments/{paymentId}/confirm-pix", paymentId.longValue())
                        .param("token", "token-invalido"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.detail").value("Token de confirmação inválido"));

        assertThat(paymentRepository.findById(paymentId.longValue()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CLOSED);
    }


    @Test
    void shouldRenderPixConfirmationHtmlPage() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createClosedOrder(token);
        String paymentBody = createPayment(token, orderId, "PIX");

        Number paymentId = JsonPath.read(paymentBody, "$.id");
        String confirmationUrl = JsonPath.read(paymentBody, "$.pixConfirmationUrl");
        String confirmationToken = extractTokenFromUrl(confirmationUrl);

        mockMvc.perform(get("/payments/{paymentId}/pix-confirmation", paymentId.longValue())
                        .param("token", confirmationToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Confirmando PIX")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/payments/" + paymentId.longValue() + "/confirm-pix?token=" + confirmationToken)));
    }

    @Test
    void shouldRenderBankSlipConfirmationHtmlPage() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createClosedOrder(token);
        String paymentBody = createPayment(token, orderId, "BANK_SLIP");

        Number paymentId = JsonPath.read(paymentBody, "$.id");
        String confirmationUrl = JsonPath.read(paymentBody, "$.bankSlipConfirmationUrl");
        String confirmationToken = extractTokenFromUrl(confirmationUrl);

        mockMvc.perform(get("/payments/{paymentId}/bank-slip-confirmation", paymentId.longValue())
                        .param("token", confirmationToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Confirmando Boleto")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/payments/" + paymentId.longValue() + "/confirm-bank-slip?token=" + confirmationToken)));
    }

    @Test
    void shouldCreateAndConfirmBankSlipPayment() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createClosedOrder(token);
        String paymentBody = createPayment(token, orderId, "BANK_SLIP");

        Number paymentId = JsonPath.read(paymentBody, "$.id");
        String confirmationUrl = JsonPath.read(paymentBody, "$.bankSlipConfirmationUrl");
        String confirmationToken = extractTokenFromUrl(confirmationUrl);

        mockMvc.perform(get("/payments/orders/{orderId}", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("BANK_SLIP"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.digitableLine", not(blankOrNullString())))
                .andExpect(jsonPath("$.bankSlipBarCode", not(blankOrNullString())))
                .andExpect(jsonPath("$.bankSlipBarCodeBase64").value("data:image/png;base64,boleto-test"));

        mockMvc.perform(post("/payments/{paymentId}/confirm-bank-slip", paymentId.longValue())
                        .param("token", confirmationToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.confirmedAt", not(blankOrNullString())));

        Order paidOrder = orderRepository.findById(orderId).orElseThrow();
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();

        assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paidOrder.getPaymentMethod().name()).isEqualTo("BANK_SLIP");
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(3);
        assertThat(updatedProduct.getReservedQuantity()).isZero();
    }

    @Test
    void shouldCancelPendingPixPayment() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createClosedOrder(token);
        String paymentBody = createPayment(token, orderId, "PIX");

        Number paymentId = JsonPath.read(paymentBody, "$.id");

        mockMvc.perform(post("/payments/{paymentId}/cancel", paymentId.longValue())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt", not(blankOrNullString())));

        Order order = orderRepository.findById(orderId).orElseThrow();
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CLOSED);
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(5);
        assertThat(updatedProduct.getReservedQuantity()).isEqualTo(2);
    }


    @Test
    void shouldRejectCancelApprovedCreditCardPayment() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        Long orderId = createClosedOrder(token);

        String responseBody = mockMvc.perform(post("/payments/orders/{orderId}", orderId)
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
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number paymentId = JsonPath.read(responseBody, "$.id");

        mockMvc.perform(post("/payments/{paymentId}/cancel", paymentId.longValue())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Não é possível cancelar pagamento aprovado"));
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

    private String createPayment(String token, Long orderId, String method) throws Exception {
        return mockMvc.perform(post("/payments/orders/{orderId}", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentMethod": "%s"
                                }
                                """.formatted(method)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();
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

    private String extractTokenFromUrl(String url) {
        int index = url.indexOf("token=");
        if (index < 0) {
            throw new IllegalArgumentException("URL de confirmação sem token: " + url);
        }
        return url.substring(index + "token=".length());
    }
}
