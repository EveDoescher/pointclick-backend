package com.pim.ecommerce.controller;

import com.jayway.jsonpath.JsonPath;
import com.pim.ecommerce.domain.entity.Product;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.entity.enums.UserRole;
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
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Product keyboard;
    private Product mouse;
    private Product notebook;
    private Product inactiveProduct;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();

        createUser("Admin PointClick", "admin@pointclick.com", "123456", UserRole.ADMIN);
        createUser("Cliente PointClick", "cliente@pointclick.com", "123456", UserRole.CUSTOMER);

        keyboard = productRepository.save(product(
                "Teclado Mecânico",
                "Periféricos",
                "Teclados",
                "PointClick",
                "PC Keys 75",
                new BigDecimal("399.90"),
                10,
                2,
                true
        ));

        mouse = productRepository.save(product(
                "Mouse Gamer",
                "Periféricos",
                "Mouses",
                "PointClick",
                "PC Mouse Pro",
                new BigDecimal("199.90"),
                5,
                5,
                true
        ));

        notebook = productRepository.save(product(
                "Notebook Ultra",
                "Computadores",
                "Notebooks",
                "NovaTech",
                "Ultra 14",
                new BigDecimal("4500.00"),
                3,
                0,
                true
        ));

        inactiveProduct = productRepository.save(product(
                "Headset Inativo",
                "Áudio",
                "Headsets",
                "PointClick",
                "Sound X",
                new BigDecimal("299.90"),
                4,
                0,
                false
        ));
    }

    @Test
    void shouldCreateProductAsAdmin() throws Exception {
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");

        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Monitor 4K Premium",
                                  "description": "Monitor 4K com painel IPS e bordas finas.",
                                  "categoryGroup": "Monitores",
                                  "category": "Monitores 4K",
                                  "brand": "PointClick",
                                  "model": "PC Vision 27",
                                  "price": 1899.90,
                                  "stockQuantity": 8,
                                  "imageUrl": "/uploads/products/monitor.png",
                                  "active": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", not(blankOrNullString())))
                .andExpect(jsonPath("$.name").value("Monitor 4K Premium"))
                .andExpect(jsonPath("$.categoryGroup").value("Monitores"))
                .andExpect(jsonPath("$.availableQuantity").value(8))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldRejectCreateProductWithoutToken() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProductJson("Monitor 4K Premium")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void shouldRejectCreateProductAsCustomer() throws Exception {
        String customerToken = loginAndExtractToken("cliente@pointclick.com", "123456");

        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProductJson("Monitor 4K Premium")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void shouldListPublicCatalogWithFiltersAndOnlyActiveProducts() throws Exception {
        mockMvc.perform(get("/products")
                        .param("categoryGroup", "Periféricos")
                        .param("available", "true")
                        .param("sort", "price_desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(keyboard.getId()))
                .andExpect(jsonPath("$[0].availableQuantity").value(8))
                .andExpect(jsonPath("$[0].outOfStock").value(false));
    }

    @Test
    void shouldFindPublicProductById() throws Exception {
        mockMvc.perform(get("/products/{id}", keyboard.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(keyboard.getId()))
                .andExpect(jsonPath("$.name").value("Teclado Mecânico"))
                .andExpect(jsonPath("$.reservedQuantity").value(2))
                .andExpect(jsonPath("$.availableQuantity").value(8));
    }

    @Test
    void shouldNotFindInactiveProductInPublicRoute() throws Exception {
        mockMvc.perform(get("/products/{id}", inactiveProduct.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Produto não encontrado"));
    }

    @Test
    void shouldFindRelatedProducts() throws Exception {
        mockMvc.perform(get("/products/{id}/related", keyboard.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(mouse.getId()));
    }

    @Test
    void shouldListActiveCategoriesGroupsAndBrands() throws Exception {
        mockMvc.perform(get("/products/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItems("Teclados", "Mouses", "Notebooks")));

        mockMvc.perform(get("/products/category-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItems("Periféricos", "Computadores")));

        mockMvc.perform(get("/products/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItems("PointClick", "NovaTech")));
    }

    @Test
    void shouldListProductsForAdminIncludingInactiveProducts() throws Exception {
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");

        mockMvc.perform(get("/products/admin")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(inactiveProduct.getId()))
                .andExpect(jsonPath("$[0].active").value(false));
    }

    @Test
    void shouldFindProductByIdForAdminEvenInactive() throws Exception {
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");

        mockMvc.perform(get("/products/admin/{id}", inactiveProduct.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(inactiveProduct.getId()))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void shouldListAllAdminCategoriesAndCategoryGroups() throws Exception {
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");

        mockMvc.perform(get("/products/admin/categories")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItems("Teclados", "Mouses", "Notebooks", "Headsets")));

        mockMvc.perform(get("/products/admin/category-groups")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItems("Periféricos", "Computadores", "Áudio")));
    }

    @Test
    void shouldUpdateProductAsAdmin() throws Exception {
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");

        mockMvc.perform(put("/products/{id}", keyboard.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Teclado Mecânico Atualizado",
                                  "description": "Teclado atualizado com acabamento premium.",
                                  "categoryGroup": "Periféricos",
                                  "category": "Teclados",
                                  "brand": "PointClick",
                                  "model": "PC Keys 75 V2",
                                  "price": 349.90,
                                  "stockQuantity": 12,
                                  "imageUrl": "/uploads/products/teclado-v2.png",
                                  "active": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Teclado Mecânico Atualizado"))
                .andExpect(jsonPath("$.price").value(349.90))
                .andExpect(jsonPath("$.stockQuantity").value(12));
    }

    @Test
    void shouldDeactivateActivateAndDeleteProductAsAdmin() throws Exception {
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");

        mockMvc.perform(patch("/products/{id}/deactivate", keyboard.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(patch("/products/{id}/activate", keyboard.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(delete("/products/{id}", keyboard.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(productRepository.findById(keyboard.getId()).orElseThrow().getActive()).isFalse();
    }

    @Test
    void shouldRejectInvalidProductBody() throws Exception {
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");

        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "description": "",
                                  "categoryGroup": "",
                                  "category": "",
                                  "brand": "",
                                  "price": -10,
                                  "stockQuantity": -1,
                                  "active": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Erro de validação"));
    }

    private String validProductJson(String name) {
        return """
                {
                  "name": "%s",
                  "description": "Produto válido para teste.",
                  "categoryGroup": "Monitores",
                  "category": "Monitores 4K",
                  "brand": "PointClick",
                  "model": "PC Vision 27",
                  "price": 1899.90,
                  "stockQuantity": 8,
                  "imageUrl": "/uploads/products/monitor.png",
                  "active": true
                }
                """.formatted(name);
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
            String model,
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
                .model(model)
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
}
