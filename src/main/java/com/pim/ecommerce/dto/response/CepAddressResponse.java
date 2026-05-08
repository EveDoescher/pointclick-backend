package com.pim.ecommerce.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resposta com endereço obtido a partir do CEP")
public record CepAddressResponse(

        @Schema(description = "CEP consultado", example = "13277672")
        String cep,

        @Schema(description = "Logradouro", example = "Rua das Flores")
        String street,

        @Schema(description = "Bairro", example = "Centro")
        String district,

        @Schema(description = "Cidade", example = "Limeira")
        String city,

        @Schema(description = "Estado em formato UF", example = "SP")
        String state
) {
}