package com.pim.ecommerce.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(
        description = """
                Dados necessários para iniciar um pedido/carrinho.

                O pedido nasce com status PENDING e ainda não possui:
                - itens;
                - endereço congelado;
                - frete;
                - pagamento;
                - snapshot de preço definitivo.

                Observação:
                No frontend, normalmente o usuário não precisa informar userId.
                O ideal é usar o usuário autenticado. Este campo permanece por compatibilidade
                com o fluxo atual da API e uso administrativo.
                """
)
public record CreateOrderRequest(

        @Schema(
                description = "ID do usuário ativo que está criando o pedido",
                example = "2",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "O id do usuário é obrigatório")
        Long userId
) {
}