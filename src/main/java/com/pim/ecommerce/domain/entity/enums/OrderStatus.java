package com.pim.ecommerce.domain.entity.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = """
                Status do pedido no fluxo do e-commerce.

                Fluxo principal:
                PENDING -> CLOSED -> PAID -> SHIPPED -> DELIVERED -> FINISHED

                Fluxos alternativos:
                - PENDING ou CLOSED podem virar CANCELLED;
                - CLOSED pode voltar para PENDING se a reserva expirar;
                - FINISHED representa confirmação de recebimento pelo cliente.
                """
)
public enum OrderStatus {

    @Schema(description = "Carrinho aberto, ainda editável")
    PENDING,

    @Schema(description = "Pedido fechado, com endereço/preços congelados e estoque reservado")
    CLOSED,

    @Schema(description = "Pagamento aprovado e estoque baixado definitivamente")
    PAID,

    @Schema(description = "Pedido enviado e a caminho do cliente")
    SHIPPED,

    @Schema(description = "Pedido marcado como entregue pela loja/admin, aguardando confirmação do cliente")
    DELIVERED,

    @Schema(description = "Pedido finalizado após confirmação de recebimento pelo cliente")
    FINISHED,

    @Schema(description = "Pedido cancelado")
    CANCELLED
}