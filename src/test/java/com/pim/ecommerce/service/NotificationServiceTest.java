package com.pim.ecommerce.service;

import com.pim.ecommerce.domain.entity.Notification;
import com.pim.ecommerce.domain.entity.Order;
import com.pim.ecommerce.domain.entity.Product;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.entity.enums.NotificationType;
import com.pim.ecommerce.domain.entity.enums.UserRole;
import com.pim.ecommerce.domain.repository.FavoriteRepository;
import com.pim.ecommerce.domain.repository.NotificationRepository;
import com.pim.ecommerce.security.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void shouldListOnlyUnreadNotificationsWhenRequested() {
        Notification notification = notification(10L, user(2L), NotificationType.ORDER_SHIPPED, false);
        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(2L)).thenReturn(List.of(notification));

        var responses = notificationService.findMyNotifications(true);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).read()).isFalse();
    }

    @Test
    void shouldCountMyUnreadNotifications() {
        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(notificationRepository.countByUserIdAndReadFalse(2L)).thenReturn(3L);

        Long count = notificationService.countMyUnreadNotifications();

        assertThat(count).isEqualTo(3L);
    }

    @Test
    void shouldMarkNotificationAsRead() {
        Notification notification = notification(10L, user(2L), NotificationType.ORDER_SHIPPED, false);
        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(notificationRepository.findByIdAndUserId(10L, 2L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = notificationService.markAsRead(10L);

        assertThat(response.read()).isTrue();
        assertThat(response.readAt()).isNotNull();
    }

    @Test
    void shouldThrowWhenNotificationNotFound() {
        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(notificationRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notificação não encontrada");
    }

    @Test
    void shouldCreatePaymentApprovedNotification() {
        User user = user(2L);
        Order order = Order.builder().id(7L).user(user).build();
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.notifyPaymentApproved(order);

        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getType()).isEqualTo(NotificationType.PAYMENT_APPROVED);
        assertThat(saved.getTitle()).isEqualTo("Pagamento aprovado");
        assertThat(saved.getMessage()).contains("#7");
        assertThat(saved.getLinkUrl()).isEqualTo("/pedidos/7");
        assertThat(saved.getRead()).isFalse();
    }

    @Test
    void shouldNotifyFavoriteUsersAboutPromotion() {
        User first = user(1L);
        User second = user(2L);
        Product product = Product.builder().id(9L).name("Teclado").build();
        when(favoriteRepository.findActiveUsersByProductId(9L)).thenReturn(List.of(first, second));
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.notifyFavoriteProductPromotion(
                product,
                new BigDecimal("500.00"),
                new BigDecimal("399.90")
        );

        verify(notificationRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notification::getType)
                .containsOnly(NotificationType.FAVORITE_PRODUCT_PROMOTION);
        assertThat(captor.getAllValues().get(0).getMessage()).contains("Teclado");
    }


    @Test
    void shouldListAllNotificationsWhenUnreadOnlyIsFalse() {
        Notification first = notification(10L, user(2L), NotificationType.ORDER_SHIPPED, false);
        Notification second = notification(11L, user(2L), NotificationType.ORDER_DELIVERED, true);
        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(first, second));

        var responses = notificationService.findMyNotifications(false);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting("type")
                .containsExactly(NotificationType.ORDER_SHIPPED, NotificationType.ORDER_DELIVERED);
        verify(notificationRepository, org.mockito.Mockito.never()).findByUserIdAndReadFalseOrderByCreatedAtDesc(2L);
    }

    @Test
    void shouldListAllNotificationsWhenUnreadOnlyIsNull() {
        Notification notification = notification(10L, user(2L), NotificationType.ORDER_SHIPPED, false);
        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(notification));

        var responses = notificationService.findMyNotifications(null);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().id()).isEqualTo(10L);
    }

    @Test
    void shouldKeepReadAtWhenNotificationIsAlreadyRead() {
        Notification notification = notification(10L, user(2L), NotificationType.ORDER_SHIPPED, true);
        java.time.LocalDateTime readAt = java.time.LocalDateTime.now().minusHours(2);
        notification.setReadAt(readAt);
        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(notificationRepository.findByIdAndUserId(10L, 2L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = notificationService.markAsRead(10L);

        assertThat(response.read()).isTrue();
        assertThat(response.readAt()).isEqualTo(readAt);
    }

    @Test
    void shouldMarkAllNotificationsAsReadForCurrentUser() {
        when(currentUserService.getCurrentUserId()).thenReturn(2L);

        notificationService.markAllAsRead();

        verify(notificationRepository).markAllAsReadByUserId(2L);
    }

    @Test
    void shouldDeleteNotificationForCurrentUser() {
        Notification notification = notification(10L, user(2L), NotificationType.ORDER_SHIPPED, false);
        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(notificationRepository.findByIdAndUserId(10L, 2L)).thenReturn(Optional.of(notification));

        notificationService.delete(10L);

        verify(notificationRepository).deleteByIdAndUserId(10L, 2L);
    }

    @Test
    void shouldRejectDeleteWhenNotificationDoesNotBelongToCurrentUser() {
        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(notificationRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.delete(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notificação não encontrada");

        verify(notificationRepository, org.mockito.Mockito.never()).deleteByIdAndUserId(99L, 2L);
    }

    @Test
    void shouldCreateOrderLifecycleNotifications() {
        User user = user(2L);
        Order order = Order.builder().id(7L).user(user).build();
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.notifyOrderShipped(order);
        notificationService.notifyOrderDelivered(order);
        notificationService.notifyOrderFinished(order);
        notificationService.notifyOrderCancelled(order);

        verify(notificationRepository, org.mockito.Mockito.times(4)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notification::getType)
                .containsExactly(
                        NotificationType.ORDER_SHIPPED,
                        NotificationType.ORDER_DELIVERED,
                        NotificationType.ORDER_FINISHED,
                        NotificationType.ORDER_CANCELLED
                );
        assertThat(captor.getAllValues()).extracting(Notification::getLinkUrl)
                .contains("/pedidos/7", "/pedidos/7#avaliacao");
    }

    @Test
    void shouldNotCreateFavoritePromotionNotificationWhenThereAreNoUsers() {
        Product product = Product.builder().id(9L).name("Teclado").build();
        when(favoriteRepository.findActiveUsersByProductId(9L)).thenReturn(List.of());

        notificationService.notifyFavoriteProductPromotion(product, new BigDecimal("500.00"), new BigDecimal("399.90"));

        verify(notificationRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void shouldNotifyFavoriteUsersWhenProductIsBackInStock() {
        User user = user(1L);
        Product product = Product.builder().id(9L).name("Teclado").build();
        when(favoriteRepository.findActiveUsersByProductId(9L)).thenReturn(List.of(user));
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.notifyFavoriteProductBackInStock(product);

        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.FAVORITE_PRODUCT_BACK_IN_STOCK);
        assertThat(captor.getValue().getTitle()).isEqualTo("Produto favorito voltou ao estoque");
        assertThat(captor.getValue().getMessage()).contains("Teclado").contains("voltou ao estoque");
        assertThat(captor.getValue().getLinkUrl()).isEqualTo("/produtos/9");
    }

    private Notification notification(Long id, User user, NotificationType type, Boolean read) {
        return Notification.builder()
                .id(id)
                .user(user)
                .type(type)
                .title("Título")
                .message("Mensagem")
                .linkUrl("/pedidos/1")
                .read(read)
                .build();
    }

    private User user(Long id) {
        return User.builder()
                .id(id)
                .fullName("Usuário " + id)
                .email("user" + id + "@email.com")
                .passwordHash("hash")
                .role(UserRole.CUSTOMER)
                .active(true)
                .build();
    }
}
