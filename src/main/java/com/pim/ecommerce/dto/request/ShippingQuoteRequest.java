package com.pim.ecommerce.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para simular frete")
public record ShippingQuoteRequest(

        @Schema(
                description = "CEP de destino para cálculo do frete. Pode conter máscara.",
                example = "13277-672",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "CEP é obrigatório")
        @Size(max = 20, message = "CEP deve ter no máximo 20 caracteres")
        String cep
) {
}