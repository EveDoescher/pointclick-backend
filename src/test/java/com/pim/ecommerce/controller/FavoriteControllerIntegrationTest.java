package com.pim.ecommerce.controller;

import com.jayway.jsonpath.JsonPath;
import com.pim.ecommerce.domain.entity.Product;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.entity.enums.UserRole;
import com.pim.ecommerce.domain.repository.FavoriteRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FavoriteControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User customer;
    private Product product;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();

        customer = createUser("Ana Souza", "ana@email.com", "123456", UserRole.CUSTOMER);
        product = productRepository.save(product("Teclado Mecânico", new BigDecimal("399.90"), true));
    }

    @Test
    void shouldAddProductToFavorites() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");

        mockMvc.perform(post("/favorites/products/{productId}", product.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", not(blankOrNullString())))
                .andExpect(jsonPath("$.userId").value(customer.getId()))
                .andExpect(jsonPath("$.product.id").value(product.getId()))
                .andExpect(jsonPath("$.product.name").value("Teclado Mecânico"));

        assertThat(favoriteRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldNotDuplicateFavoriteWhenAddingSameProductAgain() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");

        addFavorite(token, product.getId());
        addFavorite(token, product.getId());

        assertThat(favoriteRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldListMyFavorites() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        addFavorite(token, product.getId());

        mockMvc.perform(get("/favorites")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].product.id").value(product.getId()))
                .andExpect(jsonPath("$[0].product.name").value("Teclado Mecânico"));
    }

    @Test
    void shouldCheckIfProductIsFavorited() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");

        mockMvc.perform(get("/favorites/products/{productId}/exists", product.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(false));

        addFavorite(token, product.getId());

        mockMvc.perform(get("/favorites/products/{productId}/exists", product.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(true));
    }

    @Test
    void shouldRemoveFavorite() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        addFavorite(token, product.getId());

        mockMvc.perform(delete("/favorites/products/{productId}", product.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(favoriteRepository.count()).isZero();
    }

    @Test
    void shouldReturnUnauthorizedWhenListingFavoritesWithoutToken() throws Exception {
        mockMvc.perform(get("/favorites"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Não autenticado"));
    }

    private void addFavorite(String token, Long productId) throws Exception {
        mockMvc.perform(post("/favorites/products/{productId}", productId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
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

    private Product product(String name, BigDecimal price, Boolean active) {
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
}
