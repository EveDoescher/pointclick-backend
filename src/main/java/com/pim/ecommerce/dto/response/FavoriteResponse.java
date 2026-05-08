package com.pim.ecommerce.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Resposta com dados de produto favoritado")
public record FavoriteResponse(

        @Schema(description = "ID do favorito", example = "8")
        Long id,

        @Schema(description = "ID do usuário dono do favorito", example = "2")
        Long userId,

        @Schema(description = "Produto favoritado")
        ProductResponse product,

        @Schema(description = "Data em que o produto foi favoritado", example = "2026-05-02T20:00:00")
        LocalDateTime createdAt
) {
}