package com.pim.ecommerce.dto.response;

import com.pim.ecommerce.domain.entity.enums.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Resposta com dados de uma notificação do usuário")
public record NotificationResponse(

        @Schema(description = "ID da notificação", example = "15")
        Long id,

        @Schema(description = "Tipo da notificação", example = "ORDER_SHIPPED")
        NotificationType type,

        @Schema(description = "Título da notificação", example = "Seu pedido está a caminho")
        String title,

        @Schema(description = "Mensagem da notificação", example = "O pedido #12 foi enviado e está a caminho.")
        String message,

        @Schema(description = "Link interno relacionado à notificação", example = "/pedidos/12", nullable = true)
        String linkUrl,

        @Schema(description = "Indica se a notificação já foi lida", example = "false")
        Boolean read,

        @Schema(description = "Data em que a notificação foi lida", example = "2026-05-02T20:15:00", nullable = true)
        LocalDateTime readAt,

        @Schema(description = "Data de criação da notificação", example = "2026-05-02T20:00:00")
        LocalDateTime createdAt
) {
}