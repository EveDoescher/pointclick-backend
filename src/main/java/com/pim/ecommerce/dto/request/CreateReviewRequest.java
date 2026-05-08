package com.pim.ecommerce.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para criação de avaliação de produto")
public record CreateReviewRequest(

        @Schema(
                description = "Nota da avaliação, de 1 a 5",
                example = "5",
                minimum = "1",
                maximum = "5",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Nota é obrigatória")
        @Min(value = 1, message = "Nota deve ser no mínimo 1")
        @Max(value = 5, message = "Nota deve ser no máximo 5")
        Integer rating,

        @Schema(
                description = "Comentário opcional da avaliação",
                example = "Produto excelente, chegou bem embalado e funciona perfeitamente.",
                nullable = true
        )
        @Size(max = 500, message = "Comentário deve ter no máximo 500 caracteres")
        String comment
) {
}