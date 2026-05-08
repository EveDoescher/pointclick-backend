package com.pim.ecommerce.controller;

import com.jayway.jsonpath.JsonPath;
import com.pim.ecommerce.domain.entity.Order;
import com.pim.ecommerce.domain.entity.Product;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.entity.enums.OrderStatus;
import com.pim.ecommerce.domain.entity.enums.UserRole;
import com.pim.ecommerce.domain.repository.OrderRepository;
import com.pim.ecommerce.domain.repository.ProductRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerIntegrationTest extends AbstractIntegrationTest {

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

    private User admin;
    private User customer;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();

        admin = createUser("Admin PointClick", "admin@pointclick.com", "123456", UserRole.ADMIN, true);
        customer = createUser("Ana Souza", "ana@email.com", "123456", UserRole.CUSTOMER, true);
        createUser("Cliente Inativo", "inativo@email.com", "123456", UserRole.CUSTOMER, false);

        productRepository.save(product("Produto Ativo", new BigDecimal("100.00"), 10, 0, true));
        productRepository.save(product("Produto Inativo", new BigDecimal("100.00"), 10, 0, false));
        productRepository.save(product("Produto Sem Estoque", new BigDecimal("100.00"), 0, 0, true));
        productRepository.save(product("Produto Estoque Baixo", new BigDecimal("100.00"), 3, 0, true));

        orderRepository.save(order(customer, OrderStatus.PENDING, new BigDecimal("10.00")));
        orderRepository.save(order(customer, OrderStatus.CLOSED, new BigDecimal("20.00")));
        orderRepository.save(order(customer, OrderStatus.PAID, new BigDecimal("100.00")));
        orderRepository.save(order(customer, OrderStatus.SHIPPED, new BigDecimal("50.00")));
        orderRepository.save(order(customer, OrderStatus.DELIVERED, new BigDecimal("25.00")));
        orderRepository.save(order(customer, OrderStatus.FINISHED, new BigDecimal("200.00")));
        orderRepository.save(order(customer, OrderStatus.CANCELLED, new BigDecimal("999.00")));
    }

    @Test
    void shouldReturnDashboardForAdmin() throws Exception {
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");

        mockMvc.perform(get("/admin/dashboard")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(7))
                .andExpect(jsonPath("$.pendingOrders").value(1))
                .andExpect(jsonPath("$.closedOrders").value(1))
                .andExpect(jsonPath("$.paidOrders").value(1))
                .andExpect(jsonPath("$.shippedOrders").value(1))
                .andExpect(jsonPath("$.deliveredOrders").value(1))
                .andExpect(jsonPath("$.finishedOrders").value(1))
                .andExpect(jsonPath("$.cancelledOrders").value(1))
                .andExpect(jsonPath("$.totalRevenue").value(375.00))
                .andExpect(jsonPath("$.activeProducts").value(3))
                .andExpect(jsonPath("$.inactiveProducts").value(1))
                .andExpect(jsonPath("$.outOfStockProducts").value(1))
                .andExpect(jsonPath("$.lowStockProducts").value(1))
                .andExpect(jsonPath("$.activeUsers").value(2))
                .andExpect(jsonPath("$.inactiveUsers").value(1))
                .andExpect(jsonPath("$.customerUsers").value(2))
                .andExpect(jsonPath("$.adminUsers").value(1));
    }

    @Test
    void shouldRejectDashboardWithoutToken() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Não autenticado"));
    }

    @Test
    void shouldRejectDashboardAsCustomer() throws Exception {
        String customerToken = loginAndExtractToken("ana@email.com", "123456");

        mockMvc.perform(get("/admin/dashboard")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Acesso negado"));
    }

    private User createUser(String fullName, String email, String password, UserRole role, boolean active) {
        return userRepository.save(User.builder()
                .fullName(fullName)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(role)
                .active(active)
                .build());
    }

    private Product product(String name, BigDecimal price, Integer stockQuantity, Integer reservedQuantity, Boolean active) {
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
                .active(active)
                .build();
    }

    private Order order(User user, OrderStatus status, BigDecimal totalAmount) {
        return Order.builder()
                .user(user)
                .status(status)
                .itemsAmount(totalAmount)
                .discountAmount(BigDecimal.ZERO)
                .freightAmount(BigDecimal.ZERO)
                .totalAmount(totalAmount)
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
}
