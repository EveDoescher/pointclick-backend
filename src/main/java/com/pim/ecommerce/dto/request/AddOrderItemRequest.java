package com.pim.ecommerce.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(
        description = """
                Dados para adicionar um produto ao pedido.

                Regras:
                - O pedido precisa estar com status PENDING;
                - O produto precisa estar ativo;
                - A quantidade deve ser maior que zero;
                - A quantidade não pode ultrapassar o estoque disponível.
                """
)
public record AddOrderItemRequest(

        @Schema(
                description = "ID do produto que será adicionado ao pedido",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "O id do produto é obrigatório")
        Long productId,

        @Schema(
                description = "Quantidade do produto que será adicionada",
                example = "2",
                minimum = "1",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "A quantidade é obrigatória")
        @Min(value = 1, message = "A quantidade deve ser no mínimo 1")
        Integer quantity
) {
}