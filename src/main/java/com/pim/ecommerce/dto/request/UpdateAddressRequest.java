package com.pim.ecommerce.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(
        description = """
                Dados para cadastrar ou atualizar o endereço principal do usuário logado.

                Este endpoint é usado principalmente:
                - na tela Minha Conta;
                - no checkout, quando o usuário tenta fechar pedido sem endereço;
                - para corrigir endereço antes de finalizar a compra.
                """
)
public record UpdateAddressRequest(

        @Schema(
                description = "CEP atualizado do endereço. Pode conter máscara.",
                example = "13277-672",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "CEP é obrigatório")
        @Size(max = 20, message = "CEP deve ter no máximo 20 caracteres")
        String cep,

        @Schema(
                description = "Rua atualizada",
                example = "Rua das Flores",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Rua é obrigatória")
        @Size(max = 150, message = "Rua deve ter no máximo 150 caracteres")
        String street,

        @Schema(
                description = "Número atualizado",
                example = "200",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Número é obrigatório")
        @Size(max = 20, message = "Número deve ter no máximo 20 caracteres")
        String number,

        @Schema(
                description = "Complemento atualizado",
                example = "Apartamento 12",
                nullable = true
        )
        @Size(max = 100, message = "Complemento deve ter no máximo 100 caracteres")
        String complement,

        @Schema(
                description = "Cidade atualizada",
                example = "Limeira",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Cidade é obrigatória")
        @Size(max = 100, message = "Cidade deve ter no máximo 100 caracteres")
        String city,

        @Schema(
                description = "Estado atualizado em formato UF",
                example = "SP",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Estado é obrigatório")
        @Pattern(regexp = "^[A-Z]{2}$", message = "Estado deve conter 2 letras maiúsculas")
        String state
) {
}