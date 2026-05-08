package com.pim.ecommerce.controller;

import com.jayway.jsonpath.JsonPath;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.entity.enums.UserRole;
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

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UploadControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();

        createUser("Admin PointClick", "admin@pointclick.com", "123456", UserRole.ADMIN);
        createUser("Cliente PointClick", "cliente@pointclick.com", "123456", UserRole.CUSTOMER);
    }

    @Test
    void shouldUploadProductImageAsAdmin() throws Exception {
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "teclado.png",
                "image/png",
                "fake-image-content".getBytes()
        );

        mockMvc.perform(multipart("/uploads/products")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", startsWith("/uploads/products/")));
    }

    @Test
    void shouldRejectUploadWithoutToken() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "teclado.png",
                "image/png",
                "fake-image-content".getBytes()
        );

        mockMvc.perform(multipart("/uploads/products").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Não autenticado"));
    }

    @Test
    void shouldRejectUploadAsCustomer() throws Exception {
        String customerToken = loginAndExtractToken("cliente@pointclick.com", "123456");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "teclado.png",
                "image/png",
                "fake-image-content".getBytes()
        );

        mockMvc.perform(multipart("/uploads/products")
                        .file(file)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Acesso negado"));
    }

    @Test
    void shouldRejectInvalidFileType() throws Exception {
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "documento.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "texto".getBytes()
        );

        mockMvc.perform(multipart("/uploads/products")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.detail").value("Formato de imagem não permitido. Use JPG, PNG, WEBP ou GIF"));
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
