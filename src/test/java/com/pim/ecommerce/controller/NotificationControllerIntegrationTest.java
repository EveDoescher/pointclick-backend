package com.pim.ecommerce.controller;

import com.jayway.jsonpath.JsonPath;
import com.pim.ecommerce.domain.entity.Notification;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.entity.enums.NotificationType;
import com.pim.ecommerce.domain.entity.enums.UserRole;
import com.pim.ecommerce.domain.repository.NotificationRepository;
import com.pim.ecommerce.domain.repository.UserRepository;
import com.pim.ecommerce.support.AbstractIntegrationTest;
import com.pim.ecommerce.support.TestDatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User customer;
    private User otherCustomer;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();

        customer = createUser("Ana Souza", "ana@email.com", "123456", UserRole.CUSTOMER);
        otherCustomer = createUser("Outro Cliente", "outro@email.com", "123456", UserRole.CUSTOMER);
    }

    @Test
    void shouldListOnlyCurrentUserNotifications() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        createNotification(customer, NotificationType.PAYMENT_APPROVED, "Pagamento aprovado", false);
        createNotification(customer, NotificationType.ORDER_SHIPPED, "Pedido enviado", true);
        createNotification(otherCustomer, NotificationType.ORDER_CANCELLED, "Outro usuário", false);

        mockMvc.perform(get("/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void shouldFilterUnreadNotificationsAndCountUnread() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        createNotification(customer, NotificationType.PAYMENT_APPROVED, "Pagamento aprovado", false);
        createNotification(customer, NotificationType.ORDER_SHIPPED, "Pedido enviado", true);

        mockMvc.perform(get("/notifications")
                        .param("unreadOnly", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].read").value(false));

        mockMvc.perform(get("/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void shouldMarkNotificationAsRead() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        Notification notification = createNotification(customer, NotificationType.PAYMENT_APPROVED, "Pagamento aprovado", false);

        mockMvc.perform(patch("/notifications/{notificationId}/read", notification.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notification.getId()))
                .andExpect(jsonPath("$.read").value(true));

        Notification updatedNotification = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(updatedNotification.getRead()).isTrue();
        assertThat(updatedNotification.getReadAt()).isNotNull();
    }

    @Test
    void shouldMarkAllNotificationsAsReadOnlyForCurrentUser() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        createNotification(customer, NotificationType.PAYMENT_APPROVED, "Pagamento aprovado", false);
        createNotification(customer, NotificationType.ORDER_SHIPPED, "Pedido enviado", false);
        Notification otherNotification = createNotification(otherCustomer, NotificationType.ORDER_CANCELLED, "Outro usuário", false);

        mockMvc.perform(patch("/notifications/read-all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(notificationRepository.countByUserIdAndReadFalse(customer.getId())).isZero();
        assertThat(notificationRepository.findById(otherNotification.getId()).orElseThrow().getRead()).isFalse();
    }

    @Test
    void shouldDeleteOwnNotification() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        Notification notification = createNotification(customer, NotificationType.PAYMENT_APPROVED, "Pagamento aprovado", false);

        mockMvc.perform(delete("/notifications/{notificationId}", notification.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(notificationRepository.findById(notification.getId())).isEmpty();
    }

    @Test
    void shouldReturnBadRequestWhenTryingToReadAnotherUserNotification() throws Exception {
        String token = loginAndExtractToken("ana@email.com", "123456");
        Notification otherNotification = createNotification(otherCustomer, NotificationType.ORDER_CANCELLED, "Outro usuário", false);

        mockMvc.perform(patch("/notifications/{notificationId}/read", otherNotification.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Notificação não encontrada"));
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

    private Notification createNotification(User user, NotificationType type, String title, boolean read) {
        return notificationRepository.save(Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .message("Mensagem da notificação")
                .linkUrl("/pedidos/1")
                .read(read)
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
