package com.pim.ecommerce.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Resposta com dados de avaliação de produto")
public record ReviewResponse(

        @Schema(description = "ID da avaliação", example = "20")
        Long id,

        @Schema(description = "ID do pedido vinculado à avaliação", example = "10")
        Long orderId,

        @Schema(description = "ID do produto avaliado", example = "1")
        Long productId,

        @Schema(description = "Nome do produto avaliado", example = "Teclado Mecânico Premium RGB Wireless")
        String productName,

        @Schema(description = "ID do usuário que avaliou", example = "2")
        Long userId,

        @Schema(description = "Nome do usuário que avaliou", example = "Ana Souza")
        String userFullName,

        @Schema(description = "URL da foto/avatar do usuário", example = "/uploads/users/avatars/user-2.png", nullable = true)
        String userAvatarUrl,

        @Schema(description = "Nota da avaliação", example = "5")
        Integer rating,

        @Schema(description = "Comentário da avaliação", example = "Produto excelente, chegou bem embalado e funciona perfeitamente.")
        String comment,

        @Schema(description = "Quantidade de imagens anexadas", example = "3")
        Integer imageCount,

        @Schema(description = "Imagens anexadas à avaliação")
        List<ReviewImageResponse> images,

        @Schema(description = "Indica se a avaliação está ativa", example = "true")
        Boolean active,

        @Schema(description = "Data de criação da avaliação", example = "2026-05-02T20:00:00")
        LocalDateTime createdAt,

        @Schema(description = "Data da última atualização da avaliação", example = "2026-05-02T20:10:00")
        LocalDateTime updatedAt
) {
}