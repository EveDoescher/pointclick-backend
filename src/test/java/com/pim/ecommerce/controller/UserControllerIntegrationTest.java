package com.pim.ecommerce.controller;

import com.jayway.jsonpath.JsonPath;
import com.pim.ecommerce.domain.entity.Address;
import com.pim.ecommerce.domain.entity.Order;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.entity.enums.OrderStatus;
import com.pim.ecommerce.domain.entity.enums.UserRole;
import com.pim.ecommerce.domain.repository.OrderRepository;
import com.pim.ecommerce.domain.repository.UserRepository;
import com.pim.ecommerce.support.AbstractIntegrationTest;
import com.pim.ecommerce.support.TestDatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User admin;
    private User customer;
    private User otherCustomer;
    private User inactiveCustomer;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();

        admin = createUser("Admin PointClick", "admin@pointclick.com", "123456", UserRole.ADMIN, true, null);
        customer = createUser("Ana Souza", "ana@email.com", "123456", UserRole.CUSTOMER, true, null);
        otherCustomer = createUser("Bruno Lima", "bruno@email.com", "123456", UserRole.CUSTOMER, true, null);
        inactiveCustomer = createUser("Cliente Inativo", "inativo@email.com", "123456", UserRole.CUSTOMER, false, null);
    }

    @Test
    void shouldCreateUserWithCustomerRoleAndWithoutPasswordInResponse() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Carla Mendes",
                                  "email": "carla@email.com",
                                  "password": "123456"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", not(blankOrNullString())))
                .andExpect(jsonPath("$.fullName").value("Carla Mendes"))
                .andExpect(jsonPath("$.email").value("carla@email.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void shouldRejectDuplicatedEmail() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Ana Souza Duplicada",
                                  "email": "ana@email.com",
                                  "password": "123456"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Já existe um usuário com este e-mail"));
    }

    @Test
    void shouldRejectInvalidCreateUserBody() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "A",
                                  "email": "email-invalido",
                                  "password": "123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Erro de validação"));
    }

    @Test
    void shouldFindCurrentUserWithValidToken() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");

        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customer.getId()))
                .andExpect(jsonPath("$.email").value("ana@email.com"));
    }

    @Test
    void shouldReturnUnauthorizedWhenFindingCurrentUserWithoutToken() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void shouldUpdateCurrentUserProfile() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");

        mockMvc.perform(patch("/users/me/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Ana Souza Silva",
                                  "cpf": "123.456.789-00",
                                  "phone": "(19) 99999-9999"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ana Souza Silva"))
                .andExpect(jsonPath("$.cpf").value("12345678900"))
                .andExpect(jsonPath("$.phone").value("(19) 99999-9999"));
    }

    @Test
    void shouldUpdateCurrentUserAddress() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");

        mockMvc.perform(put("/users/me/address")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("13480-370", "Rua das Flores", "123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address.cep").value("13480370"))
                .andExpect(jsonPath("$.address.street").value("Rua das Flores"))
                .andExpect(jsonPath("$.profileCompleteForCheckout").value(true));
    }

    @Test
    void shouldUpdateCurrentUserAvatar() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                new byte[]{1, 2, 3, 4}
        );

        mockMvc.perform(multipart("/users/me/avatar")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl", not(blankOrNullString())));
    }

    @Test
    void shouldListUsersForAdminWithFilters() throws Exception {
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");

        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("active", "true")
                        .param("role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void shouldRejectListUsersAsCustomer() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");

        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void shouldFindUserByIdAsSelfAndAdmin() throws Exception {
        String customerToken = loginAndExtractToken("ana@email.com", "123456");
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");

        mockMvc.perform(get("/users/{id}", customer.getId())
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customer.getId()));

        mockMvc.perform(get("/users/{id}", otherCustomer.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(otherCustomer.getId()));
    }

    @Test
    void shouldRejectCustomerFindingAnotherUser() throws Exception {
        String customerToken = loginAndExtractToken("ana@email.com", "123456");

        mockMvc.perform(get("/users/{id}", otherCustomer.getId())
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acesso negado"));
    }

    @Test
    void shouldFindInactiveUserByIdForAdmin() throws Exception {
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");

        mockMvc.perform(get("/users/admin/{id}", inactiveCustomer.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(inactiveCustomer.getId()))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void shouldUpdateUserAsAdmin() throws Exception {
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");

        mockMvc.perform(put("/users/{id}", otherCustomer.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Bruno Lima Atualizado",
                                  "email": "bruno.atualizado@email.com",
                                  "cpf": "987.654.321-00",
                                  "phone": "(19) 98888-7777",
                                  "address": {
                                    "cep": "13480-370",
                                    "street": "Rua Nova",
                                    "number": "555",
                                    "complement": "Casa",
                                    "city": "Limeira",
                                    "state": "SP"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Bruno Lima Atualizado"))
                .andExpect(jsonPath("$.email").value("bruno.atualizado@email.com"))
                .andExpect(jsonPath("$.address.street").value("Rua Nova"));
    }

    @Test
    void shouldDeleteAndReactivateUserAsAdmin() throws Exception {
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");

        mockMvc.perform(delete("/users/{id}", otherCustomer.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(otherCustomer.getId()).orElseThrow().getActive()).isFalse();

        mockMvc.perform(patch("/users/{id}/activate", otherCustomer.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldListOrdersByUserId() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        orderRepository.save(Order.builder()
                .user(customer)
                .status(OrderStatus.PENDING)
                .itemsAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .freightAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .build());

        mockMvc.perform(get("/users/{id}/orders", customer.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    private User createUser(String fullName, String email, String password, UserRole role, boolean active, Address address) {
        User user = User.builder()
                .fullName(fullName)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(role)
                .active(active)
                .address(address)
                .build();

        return userRepository.save(user);
    }

    private String addressJson(String cep, String street, String number) {
        return """
                {
                  "cep": "%s",
                  "street": "%s",
                  "number": "%s",
                  "complement": "Casa",
                  "city": "Limeira",
                  "state": "SP"
                }
                """.formatted(cep, street, number);
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
