package com.pim.ecommerce.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(
        description = """
                Dados de endereço.

                Observação:
                Este DTO pode ser removido caso não exista mais nenhum fluxo de cadastro
                que envie endereço junto com outro request.
                Para atualização do endereço do usuário logado, use UpdateAddressRequest.
                """
)
public record AddressRequest(

        @Schema(
                description = "CEP do endereço. Pode conter máscara.",
                example = "13277-672",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "CEP é obrigatório")
        @Size(max = 20, message = "CEP deve ter no máximo 20 caracteres")
        String cep,

        @Schema(
                description = "Nome da rua, avenida ou logradouro",
                example = "Rua das Flores",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Rua é obrigatória")
        @Size(max = 150, message = "Rua deve ter no máximo 150 caracteres")
        String street,

        @Schema(
                description = "Número do endereço",
                example = "150",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Número é obrigatório")
        @Size(max = 20, message = "Número deve ter no máximo 20 caracteres")
        String number,

        @Schema(
                description = "Complemento do endereço",
                example = "Casa",
                nullable = true
        )
        @Size(max = 100, message = "Complemento deve ter no máximo 100 caracteres")
        String complement,

        @Schema(
                description = "Cidade do endereço",
                example = "Limeira",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Cidade é obrigatória")
        @Size(max = 100, message = "Cidade deve ter no máximo 100 caracteres")
        String city,

        @Schema(
                description = "Estado em formato UF com duas letras maiúsculas",
                example = "SP",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Estado é obrigatório")
        @Pattern(regexp = "^[A-Z]{2}$", message = "Estado deve conter 2 letras maiúsculas")
        String state
) {
}