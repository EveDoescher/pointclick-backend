package com.pim.ecommerce.dto.response;

import com.pim.ecommerce.domain.entity.enums.OrderStatus;
import com.pim.ecommerce.domain.entity.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Resumo administrativo de pedido")
public record AdminOrderSummaryResponse(

        @Schema(description = "ID do pedido", example = "10")
        Long id,

        @Schema(description = "ID do cliente", example = "4")
        Long customerId,

        @Schema(description = "Nome do cliente", example = "Lucas Cliente")
        String customerName,

        @Schema(description = "E-mail do cliente", example = "lucas@email.com")
        String customerEmail,

        @Schema(description = "Status do pedido", example = "PAID")
        OrderStatus status,

        @Schema(description = "Valor total dos itens", example = "799.80")
        BigDecimal itemsAmount,

        @Schema(description = "Valor de desconto aplicado por cupom", example = "79.98")
        BigDecimal discountAmount,

        @Schema(description = "Valor do frete", example = "15.00")
        BigDecimal freightAmount,

        @Schema(description = "Valor total do pedido", example = "734.82")
        BigDecimal totalAmount,

        @Schema(description = "Código do cupom aplicado", example = "POINT10", nullable = true)
        String couponCode,

        @Schema(description = "Forma de pagamento", example = "PIX", nullable = true)
        PaymentMethod paymentMethod,

        @Schema(description = "Data em que o pedido foi pago", example = "2026-04-27T20:15:00", nullable = true)
        LocalDateTime paidAt,

        @Schema(description = "Data em que o pedido foi enviado", example = "2026-04-27T20:30:00", nullable = true)
        LocalDateTime shippedAt,

        @Schema(description = "Data em que o pedido foi marcado como entregue", example = "2026-04-28T14:30:00", nullable = true)
        LocalDateTime deliveredAt,

        @Schema(description = "Data em que o cliente confirmou recebimento", example = "2026-04-28T15:00:00", nullable = true)
        LocalDateTime finishedAt,

        @Schema(description = "Data de criação do pedido", example = "2026-05-01T15:30:00")
        LocalDateTime createdAt
) {
}