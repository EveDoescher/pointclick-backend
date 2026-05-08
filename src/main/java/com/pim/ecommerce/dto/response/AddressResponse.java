package com.pim.ecommerce.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Resposta com os dados de endereço do usuário")
public record AddressResponse(

        @Schema(description = "ID do endereço", example = "1")
        Long id,

        @Schema(description = "CEP do endereço", example = "13277672")
        String cep,

        @Schema(description = "Rua do endereço", example = "Rua das Flores")
        String street,

        @Schema(description = "Número do endereço", example = "150")
        String number,

        @Schema(description = "Complemento do endereço", example = "Casa", nullable = true)
        String complement,

        @Schema(description = "Cidade do endereço", example = "Limeira")
        String city,

        @Schema(description = "Estado em formato UF", example = "SP")
        String state,

        @Schema(description = "Data de criação do endereço", example = "2026-04-27T20:00:00")
        LocalDateTime createdAt,

        @Schema(description = "Data da última atualização do endereço", example = "2026-04-27T20:00:00")
        LocalDateTime updatedAt
) {
}