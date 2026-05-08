package com.pim.ecommerce.dto.response;

import com.pim.ecommerce.domain.entity.enums.OrderStatus;
import com.pim.ecommerce.domain.entity.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(
        description = """
                Resumo de pedido usado em listagens e histórico do usuário.

                Não traz os itens completos do pedido.
                Para detalhes completos, use GET /orders/{orderId}.
                """
)
public record OrderSummaryResponse(

        @Schema(description = "ID do pedido", example = "12")
        Long id,

        @Schema(description = "Data de criação do pedido", example = "2026-04-27T20:00:00")
        LocalDateTime orderDate,

        @Schema(description = "Valor total dos itens, sem frete e sem desconto", example = "799.80")
        BigDecimal itemsAmount,

        @Schema(description = "Valor de desconto aplicado por cupom", example = "79.98")
        BigDecimal discountAmount,

        @Schema(description = "Valor do frete", example = "15.00")
        BigDecimal freightAmount,

        @Schema(description = "Valor total do pedido", example = "734.82")
        BigDecimal totalAmount,

        @Schema(description = "Código do cupom aplicado", example = "POINT10", nullable = true)
        String couponCode,

        @Schema(
                description = "Status atual do pedido",
                example = "DELIVERED",
                allowableValues = {"PENDING", "CLOSED", "PAID", "SHIPPED", "DELIVERED", "FINISHED", "CANCELLED"}
        )
        OrderStatus status,

        @Schema(
                description = "Forma de pagamento utilizada, quando já houver pagamento",
                example = "PIX",
                nullable = true
        )
        PaymentMethod paymentMethod,

        @Schema(description = "Data de criação do registro", example = "2026-04-27T20:00:00")
        LocalDateTime createdAt
) {
}