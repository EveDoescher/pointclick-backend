package com.pim.ecommerce.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Resposta com simulação de frete")
public record ShippingQuoteResponse(

        @Schema(description = "CEP usado na simulação", example = "13277672")
        String cep,

        @Schema(description = "Valor estimado do frete", example = "15.00")
        BigDecimal shippingPrice,

        @Schema(description = "Prazo estimado em dias", example = "5")
        Integer estimatedDays,

        @Schema(description = "Rua localizada pelo CEP", example = "Rua das Flores", nullable = true)
        String street,

        @Schema(description = "Bairro localizado pelo CEP", example = "Centro", nullable = true)
        String district,

        @Schema(description = "Cidade localizada pelo CEP", example = "Limeira", nullable = true)
        String city,

        @Schema(description = "Estado localizado pelo CEP", example = "SP", nullable = true)
        String state
) {
}