package com.pim.ecommerce.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(
        description = """
                Resposta com dados completos do produto.

                Inclui informações de estoque total, estoque reservado, disponibilidade real,
                indicadores administrativos e dados de avaliação.
                """
)
public record ProductResponse(

        @Schema(description = "ID do produto", example = "1")
        Long id,

        @Schema(description = "Nome comercial do produto", example = "Teclado Mecânico Premium RGB Wireless")
        String name,

        @Schema(description = "Descrição detalhada do produto")
        String description,

        @Schema(description = "Grupo comercial amplo da categoria", example = "Periféricos")
        String categoryGroup,

        @Schema(description = "Categoria do produto", example = "Teclado")
        String category,

        @Schema(description = "Marca do produto", example = "PointClick")
        String brand,

        @Schema(description = "Modelo do produto", example = "PC Keys Pro 75", nullable = true)
        String model,

        @Schema(description = "Preço atual do produto", example = "399.90")
        BigDecimal price,

        @Schema(
                description = """
                        Estoque físico total do produto.

                        Esse valor só é reduzido definitivamente quando um pagamento é aprovado.
                        """,
                example = "20"
        )
        Integer stockQuantity,

        @Schema(
                description = """
                        Quantidade reservada por pedidos CLOSED.

                        Enquanto o pedido está CLOSED, o estoque fica reservado,
                        mas ainda não baixado definitivamente.
                        """,
                example = "2"
        )
        Integer reservedQuantity,

        @Schema(
                description = """
                        Quantidade disponível para novas compras.

                        Fórmula:
                        availableQuantity = stockQuantity - reservedQuantity.
                        """,
                example = "18"
        )
        Integer availableQuantity,

        @Schema(description = "URL ou caminho da imagem do produto", example = "/uploads/products/uuid-teclado.png", nullable = true)
        String imageUrl,

        @Schema(description = "Indica se o produto está ativo no catálogo", example = "true")
        Boolean active,

        @Schema(description = "Indica se o produto está sem estoque disponível", example = "false")
        Boolean outOfStock,

        @Schema(description = "Indica se o produto está com estoque baixo", example = "false")
        Boolean lowStock,

        @Schema(description = "Quantidade de usuários que favoritaram o produto", example = "12")
        Long favoriteCount,

        @Schema(description = "Quantidade de avaliações ativas do produto", example = "8")
        Long reviewCount,

        @Schema(description = "Média das avaliações do produto", example = "4.7")
        Double averageRating,

        @Schema(description = "Data de criação do produto", example = "2026-04-27T20:00:00")
        LocalDateTime createdAt,

        @Schema(description = "Data da última atualização do produto", example = "2026-04-27T20:10:00")
        LocalDateTime updatedAt
) {
}