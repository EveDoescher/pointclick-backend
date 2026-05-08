package com.pim.ecommerce.dto.response;

import com.pim.ecommerce.domain.entity.enums.OrderStatus;
import com.pim.ecommerce.domain.entity.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(
        description = """
                Resposta completa de um pedido.

                Esse DTO é usado para:
                - criação do pedido;
                - adição de itens;
                - atualização de carrinho;
                - fechamento;
                - aplicação de cupom;
                - consulta por ID;
                - envio;
                - marcação como entregue;
                - confirmação de recebimento;
                - cancelamento.

                Fluxo esperado:
                PENDING -> CLOSED -> PAID -> SHIPPED -> DELIVERED -> FINISHED.

                Observações:
                - deliveryAddress só é preenchido no fechamento do pedido;
                - freightAmount só é calculado no fechamento;
                - paymentMethod só é preenchido quando o pagamento é criado/aprovado;
                - paidAt só é preenchido quando o pagamento é aprovado;
                - deliveredAt é preenchido quando o admin marca o pedido como entregue;
                - finishedAt é preenchido quando o cliente confirma recebimento;
                - reservationExpiresAt só existe enquanto o pedido está CLOSED.
                """
)
public record OrderResponse(

        @Schema(description = "ID do pedido", example = "12")
        Long id,

        @Schema(description = "ID do usuário dono do pedido", example = "2")
        Long userId,

        @Schema(description = "Nome completo do usuário dono do pedido", example = "Ana Souza")
        String userFullName,

        @Schema(description = "Data de criação do pedido", example = "2026-04-27T20:00:00")
        LocalDateTime orderDate,

        @Schema(
                description = "Soma dos subtotais dos itens do pedido, sem frete e sem desconto",
                example = "799.80"
        )
        BigDecimal itemsAmount,

        @Schema(
                description = "Valor de desconto aplicado por cupom",
                example = "79.98"
        )
        BigDecimal discountAmount,

        @Schema(
                description = "Valor do frete calculado no fechamento do pedido",
                example = "15.00"
        )
        BigDecimal freightAmount,

        @Schema(
                description = "Valor total do pedido: itemsAmount - discountAmount + freightAmount",
                example = "734.82"
        )
        BigDecimal totalAmount,

        @Schema(
                description = "Código do cupom aplicado no pedido",
                example = "POINT10",
                nullable = true
        )
        String couponCode,

        @Schema(
                description = """
                        Status atual do pedido.

                        PENDING: carrinho em montagem;
                        CLOSED: pedido fechado, com estoque reservado e dados congelados;
                        PAID: pagamento aprovado e estoque baixado definitivamente;
                        SHIPPED: pedido enviado e a caminho;
                        DELIVERED: pedido marcado como entregue pela loja/admin;
                        FINISHED: recebimento confirmado pelo cliente;
                        CANCELLED: pedido cancelado.
                        """,
                example = "DELIVERED",
                allowableValues = {"PENDING", "CLOSED", "PAID", "SHIPPED", "DELIVERED", "FINISHED", "CANCELLED"}
        )
        OrderStatus status,

        @Schema(
                description = "Forma de pagamento usada no pedido. Só é preenchida após criação/aprovação do pagamento.",
                example = "PIX",
                nullable = true
        )
        PaymentMethod paymentMethod,

        @Schema(
                description = """
                        Endereço de entrega congelado no momento do fechamento do pedido.

                        Se o usuário alterar o endereço depois, este campo não muda.
                        """,
                example = "Rua das Flores, 150, Casa - Limeira/SP - CEP: 13277672",
                nullable = true
        )
        String deliveryAddress,

        @Schema(
                description = "Observações ou instruções de entrega informadas no pagamento",
                example = "Entregar em horário comercial",
                nullable = true
        )
        String notes,

        @Schema(description = "Data em que o pedido foi fechado", example = "2026-04-27T20:10:00", nullable = true)
        LocalDateTime closedAt,

        @Schema(description = "Data em que o pagamento foi aprovado", example = "2026-04-27T20:15:00", nullable = true)
        LocalDateTime paidAt,

        @Schema(description = "Data em que o pedido foi enviado", example = "2026-04-27T20:30:00", nullable = true)
        LocalDateTime shippedAt,

        @Schema(description = "Data em que o pedido foi marcado como entregue", example = "2026-04-28T14:30:00", nullable = true)
        LocalDateTime deliveredAt,

        @Schema(description = "Data em que o cliente confirmou o recebimento", example = "2026-04-28T15:00:00", nullable = true)
        LocalDateTime finishedAt,

        @Schema(description = "Data em que o pedido foi cancelado", example = "2026-04-27T20:20:00", nullable = true)
        LocalDateTime cancelledAt,

        @Schema(
                description = """
                        Data de expiração da reserva de estoque.

                        É preenchida quando o pedido entra em CLOSED.
                        É limpa quando o pagamento é aprovado.
                        """,
                example = "2026-04-28T20:10:00",
                nullable = true
        )
        LocalDateTime reservationExpiresAt,

        @Schema(description = "Lista de itens do pedido")
        List<OrderItemResponse> items,

        @Schema(description = "Data de criação do registro", example = "2026-04-27T20:00:00")
        LocalDateTime createdAt,

        @Schema(description = "Data da última atualização do registro", example = "2026-04-27T20:10:00")
        LocalDateTime updatedAt
) {
}