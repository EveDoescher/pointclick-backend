package com.pim.ecommerce.service;

import com.pim.ecommerce.domain.entity.Notification;
import com.pim.ecommerce.domain.entity.Order;
import com.pim.ecommerce.domain.entity.Product;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.entity.enums.NotificationType;
import com.pim.ecommerce.domain.repository.FavoriteRepository;
import com.pim.ecommerce.domain.repository.NotificationRepository;
import com.pim.ecommerce.dto.response.NotificationResponse;
import com.pim.ecommerce.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final FavoriteRepository favoriteRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public List<NotificationResponse> findMyNotifications(Boolean unreadOnly) {
        Long currentUserId = currentUserService.getCurrentUserId();

        List<Notification> notifications = Boolean.TRUE.equals(unreadOnly)
                ? notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(currentUserId)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUserId);

        return notifications.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Long countMyUnreadNotifications() {
        Long currentUserId = currentUserService.getCurrentUserId();

        return notificationRepository.countByUserIdAndReadFalse(currentUserId);
    }

    @Transactional
    public NotificationResponse markAsRead(Long notificationId) {
        Long currentUserId = currentUserService.getCurrentUserId();

        Notification notification = notificationRepository.findByIdAndUserId(notificationId, currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("Notificação não encontrada"));

        if (!Boolean.TRUE.equals(notification.getRead())) {
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
        }

        return toResponse(notificationRepository.save(notification));
    }

    @Transactional
    public void markAllAsRead() {
        Long currentUserId = currentUserService.getCurrentUserId();

        notificationRepository.markAllAsReadByUserId(currentUserId);
    }

    @Transactional
    public void delete(Long notificationId) {
        Long currentUserId = currentUserService.getCurrentUserId();

        notificationRepository.findByIdAndUserId(notificationId, currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("Notificação não encontrada"));

        notificationRepository.deleteByIdAndUserId(notificationId, currentUserId);
    }

    @Transactional
    public void notifyPaymentApproved(Order order) {
        createOrderNotification(
                order,
                NotificationType.PAYMENT_APPROVED,
                "Pagamento aprovado",
                "O pagamento do pedido #" + order.getId() + " foi aprovado com sucesso.",
                "/pedidos/" + order.getId()
        );
    }

    @Transactional
    public void notifyOrderShipped(Order order) {
        createOrderNotification(
                order,
                NotificationType.ORDER_SHIPPED,
                "Seu pedido está a caminho",
                "O pedido #" + order.getId() + " foi enviado e está a caminho.",
                "/pedidos/" + order.getId()
        );
    }

    @Transactional
    public void notifyOrderDelivered(Order order) {
        createOrderNotification(
                order,
                NotificationType.ORDER_DELIVERED,
                "Pedido marcado como entregue",
                "O pedido #" + order.getId() + " foi marcado como entregue. Confirme o recebimento se estiver tudo certo.",
                "/pedidos/" + order.getId()
        );
    }

    @Transactional
    public void notifyOrderFinished(Order order) {
        createOrderNotification(
                order,
                NotificationType.ORDER_FINISHED,
                "Compra finalizada",
                "O recebimento do pedido #" + order.getId() + " foi confirmado. Avalie os produtos da sua compra na tela de detalhes do pedido.",
                "/pedidos/" + order.getId() + "#avaliacao"
        );
    }

    @Transactional
    public void notifyOrderCancelled(Order order) {
        createOrderNotification(
                order,
                NotificationType.ORDER_CANCELLED,
                "Pedido cancelado",
                "O pedido #" + order.getId() + " foi cancelado.",
                "/pedidos/" + order.getId()
        );
    }

    @Transactional
    public void notifyFavoriteProductPromotion(Product product, BigDecimal oldPrice, BigDecimal newPrice) {
        List<User> users = favoriteRepository.findActiveUsersByProductId(product.getId());

        if (users.isEmpty()) {
            return;
        }

        String message = "O produto " + product.getName()
                + " entrou em promoção: de "
                + formatCurrency(oldPrice)
                + " por "
                + formatCurrency(newPrice)
                + ".";

        users.forEach(user -> createNotification(
                user,
                NotificationType.FAVORITE_PRODUCT_PROMOTION,
                "Produto favorito em promoção",
                message,
                "/produtos/" + product.getId()
        ));
    }

    @Transactional
    public void notifyFavoriteProductBackInStock(Product product) {
        List<User> users = favoriteRepository.findActiveUsersByProductId(product.getId());

        if (users.isEmpty()) {
            return;
        }

        String message = "O produto " + product.getName() + " voltou ao estoque.";

        users.forEach(user -> createNotification(
                user,
                NotificationType.FAVORITE_PRODUCT_BACK_IN_STOCK,
                "Produto favorito voltou ao estoque",
                message,
                "/produtos/" + product.getId()
        ));
    }

    private void createOrderNotification(
            Order order,
            NotificationType type,
            String title,
            String message,
            String targetUrl
    ) {
        Notification notification = Notification.builder()
                .user(order.getUser())
                .type(type)
                .title(title)
                .message(message)
                .linkUrl(targetUrl)
                .read(false)
                .build();

        notificationRepository.save(notification);
    }

    private void createNotification(
            User user,
            NotificationType type,
            String title,
            String message,
            String linkUrl
    ) {
        if (user == null || !Boolean.TRUE.equals(user.getActive())) {
            return;
        }

        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .message(message)
                .linkUrl(linkUrl)
                .read(false)
                .build();

        notificationRepository.save(notification);
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getLinkUrl(),
                notification.getRead(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }

    private String formatCurrency(BigDecimal value) {
        BigDecimal safeValue = value != null ? value : BigDecimal.ZERO;

        return NumberFormat
                .getCurrencyInstance(Locale.of("pt", "BR"))
                .format(safeValue);
    }
}