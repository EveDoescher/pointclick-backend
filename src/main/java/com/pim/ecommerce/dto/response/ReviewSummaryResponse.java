package com.pim.ecommerce.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resumo das avaliações de um produto")
public record ReviewSummaryResponse(

        @Schema(description = "Média das avaliações", example = "4.8")
        Double averageRating,

        @Schema(description = "Total de avaliações", example = "120")
        Long totalReviews,

        @Schema(description = "Quantidade de avaliações com 5 estrelas", example = "90")
        Long fiveStars,

        @Schema(description = "Quantidade de avaliações com 4 estrelas", example = "20")
        Long fourStars,

        @Schema(description = "Quantidade de avaliações com 3 estrelas", example = "6")
        Long threeStars,

        @Schema(description = "Quantidade de avaliações com 2 estrelas", example = "2")
        Long twoStars,

        @Schema(description = "Quantidade de avaliações com 1 estrela", example = "2")
        Long oneStar,

        @Schema(description = "Quantidade de avaliações com comentário", example = "80")
        Long withComments,

        @Schema(description = "Quantidade de avaliações com mídia", example = "45")
        Long withMedia
) {
}