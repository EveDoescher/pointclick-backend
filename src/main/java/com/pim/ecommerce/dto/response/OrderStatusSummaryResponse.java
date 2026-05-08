package com.pim.ecommerce.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resumo administrativo de pedidos por status")
public record OrderStatusSummaryResponse(

        @Schema(description = "Quantidade de pedidos pendentes", example = "4")
        Long pending,

        @Schema(description = "Quantidade de pedidos fechados aguardando pagamento", example = "2")
        Long closed,

        @Schema(description = "Quantidade de pedidos pagos aguardando envio", example = "8")
        Long paid,

        @Schema(description = "Quantidade de pedidos enviados e a caminho", example = "3")
        Long shipped,

        @Schema(description = "Quantidade de pedidos marcados como entregues aguardando confirmação do cliente", example = "2")
        Long delivered,

        @Schema(description = "Quantidade de pedidos finalizados após confirmação do cliente", example = "20")
        Long finished,

        @Schema(description = "Quantidade de pedidos cancelados", example = "1")
        Long cancelled
) {
}