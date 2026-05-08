package com.pim.ecommerce.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(
        description = """
                Item de um pedido.

                Contém dados básicos do produto, quantidade e preço registrado no momento do pedido.
                O campo unitPriceAtMoment funciona como snapshot do preço, impedindo que alterações futuras
                no produto afetem pedidos já fechados.
                """
)
public record OrderItemResponse(

        @Schema(description = "ID do item do pedido", example = "4")
        Long id,

        @Schema(description = "ID do produto relacionado ao item", example = "1")
        Long productId,

        @Schema(description = "Nome do produto", example = "Teclado Mecânico Premium RGB Wireless")
        String productName,

        @Schema(description = "Marca do produto", example = "PointClick")
        String productBrand,

        @Schema(description = "Categoria do produto", example = "Teclado")
        String productCategory,

        @Schema(description = "URL ou caminho da imagem do produto", example = "/uploads/products/uuid-teclado.png", nullable = true)
        String productImageUrl,

        @Schema(description = "Quantidade do produto no pedido", example = "2")
        Integer quantity,

        @Schema(
                description = "Preço unitário do produto no momento do pedido",
                example = "399.90"
        )
        BigDecimal unitPriceAtMoment,

        @Schema(
                description = "Subtotal do item: quantity * unitPriceAtMoment",
                example = "799.80"
        )
        BigDecimal subtotal,

        @Schema(description = "Data de criação do item", example = "2026-04-27T20:05:00")
        LocalDateTime createdAt,

        @Schema(description = "Data da última atualização do item", example = "2026-04-27T20:05:00")
        LocalDateTime updatedAt
) {
}