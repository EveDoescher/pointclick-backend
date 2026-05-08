package com.pim.ecommerce.controller;

import com.jayway.jsonpath.JsonPath;
import com.pim.ecommerce.domain.entity.Coupon;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.entity.enums.DiscountType;
import com.pim.ecommerce.domain.entity.enums.UserRole;
import com.pim.ecommerce.domain.repository.CouponRepository;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CouponControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Coupon activeCoupon;
    private Coupon inactiveCoupon;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();

        createUser("Admin PointClick", "admin@pointclick.com", "123456", UserRole.ADMIN);
        createUser("Cliente PointClick", "cliente@pointclick.com", "123456", UserRole.CUSTOMER);

        activeCoupon = couponRepository.save(coupon(
                "POINT10",
                DiscountType.PERCENTAGE,
                new BigDecimal("10.00"),
                new BigDecimal("100.00"),
                true
        ));

        inactiveCoupon = couponRepository.save(coupon(
                "OFF25",
                DiscountType.FIXED_AMOUNT,
                new BigDecimal("25.00"),
                BigDecimal.ZERO,
                false
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
                                  "code": " frete15 ",
                                  "description": "Cupom administrativo de teste",
                                  "discountType": "FIXED_AMOUNT",
                                  "discountValue": 15.00,
                                  "minimumOrderValue": 50.00,
                                  "active": true,
                                  "startsAt": "2026-01-01T00:00:00",
                                  "endsAt": "2026-12-31T23:59:59",
                                  "usageLimit": 100
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", not(blankOrNullString())))
                .andExpect(jsonPath("$.code").value("FRETE15"))
                .andExpect(jsonPath("$.discountType").value("FIXED_AMOUNT"))
                .andExpect(jsonPath("$.discountValue").value(15.00))
                .andExpect(jsonPath("$.minimumOrderValue").value(50.00))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.usageLimit").value(100))
                .andExpect(jsonPath("$.usedCount").value(0));

        assertThat(couponRepository.findByCode("FRETE15")).isPresent();
    }

    @Test
    void shouldRejectCreateCouponAsCustomer() throws Exception {
        String customerToken = loginAndExtractToken("cliente@pointclick.com", "123456");

        mockMvc.perform(post("/coupons")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCouponJson("CUSTOMER10")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void shouldRejectCreateCouponWithoutToken() throws Exception {
        mockMvc.perform(post("/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCouponJson("SEMLOGIN")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void shouldRejectDuplicatedCouponCode() throws Exception {
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");

        mockMvc.perform(post("/coupons")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCouponJson("point10")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Já existe um cupom com este código"));
    }

    @Test
    void shouldRejectInvalidCouponBody() throws Exception {
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");

        mockMvc.perform(post("/coupons")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "",
                                  "discountType": null,
                                  "discountValue": -1,
                                  "minimumOrderValue": -10,
                                  "active": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Erro de validação"));
    }

    @Test
    void shouldListAllCouponsAsAdmin() throws Exception {
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");

        mockMvc.perform(get("/coupons")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void shouldListOnlyActiveCouponsAsAdmin() throws Exception {
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");

        mockMvc.perform(get("/coupons")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("POINT10"));
    }

    @Test
    void shouldFindCouponByIdAsAdmin() throws Exception {
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");

        mockMvc.perform(get("/coupons/{id}", activeCoupon.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(activeCoupon.getId()))
                .andExpect(jsonPath("$.code").value("POINT10"))
                .andExpect(jsonPath("$.currentlyValid").value(true));
    }

    @Test
    void shouldUpdateCouponAsAdmin() throws Exception {
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");

        mockMvc.perform(put("/coupons/{id}", activeCoupon.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "POINT15",
                                  "description": "Cupom atualizado",
                                  "discountType": "PERCENTAGE",
                                  "discountValue": 15.00,
                                  "minimumOrderValue": 200.00,
                                  "active": true,
                                  "startsAt": "2026-01-01T00:00:00",
                                  "endsAt": "2026-12-31T23:59:59",
                                  "usageLimit": 50
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("POINT15"))
                .andExpect(jsonPath("$.description").value("Cupom atualizado"))
                .andExpect(jsonPath("$.discountValue").value(15.00))
                .andExpect(jsonPath("$.minimumOrderValue").value(200.00))
                .andExpect(jsonPath("$.usageLimit").value(50));
    }

    @Test
    void shouldDeactivateAndActivateCouponAsAdmin() throws Exception {
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");

        mockMvc.perform(patch("/coupons/{id}/deactivate", activeCoupon.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(patch("/coupons/{id}/activate", activeCoupon.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldActivateInactiveCouponAsAdmin() throws Exception {
        String adminToken = loginAndExtractToken("admin@pointclick.com", "123456");

        mockMvc.perform(patch("/coupons/{id}/activate", inactiveCoupon.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(inactiveCoupon.getId()))
                .andExpect(jsonPath("$.active").value(true));
    }

    private String validCouponJson(String code) {
        return """
                {
                  "code": "%s",
                  "description": "Cupom válido para teste",
                  "discountType": "PERCENTAGE",
                  "discountValue": 10.00,
                  "minimumOrderValue": 100.00,
                  "active": true,
                  "usageLimit": 20
                }
                """.formatted(code);
    }

    private Coupon coupon(
            String code,
            DiscountType discountType,
            BigDecimal discountValue,
            BigDecimal minimumOrderValue,
            boolean active
    ) {
        return Coupon.builder()
                .code(code)
                .description("Cupom " + code)
                .discountType(discountType)
                .discountValue(discountValue)
                .minimumOrderValue(minimumOrderValue)
                .active(active)
                .startsAt(LocalDateTime.now().minusDays(1))
                .endsAt(LocalDateTime.now().plusDays(30))
                .usageLimit(10)
                .usedCount(0)
                .build();
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
