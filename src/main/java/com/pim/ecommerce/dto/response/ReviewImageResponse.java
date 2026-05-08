package com.pim.ecommerce.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Imagem anexada a uma avaliação")
public record ReviewImageResponse(

        @Schema(description = "ID da imagem", example = "1")
        Long id,

        @Schema(description = "URL pública da imagem", example = "/uploads/reviews/review-image.png")
        String imageUrl,

        @Schema(description = "Ordem de exibição", example = "0")
        Integer displayOrder
) {
}