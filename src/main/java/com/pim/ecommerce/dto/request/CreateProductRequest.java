package com.pim.ecommerce.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(
        description = """
                Dados para cadastro de um produto no catálogo.

                Produto representa um item eletrônico disponível para compra.
                A imagem deve ser enviada previamente em POST /uploads/products.
                O endpoint de upload retorna uma URL pública que deve ser enviada no campo imageUrl.
                """
)
public record CreateProductRequest(

        @Schema(
                description = "Nome comercial do produto",
                example = "Teclado Mecânico Premium RGB Wireless",
                maxLength = 150,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 150, message = "Nome deve ter no máximo 150 caracteres")
        String name,

        @Schema(
                description = "Descrição detalhada do produto",
                example = "Teclado mecânico premium sem fio com layout compacto, iluminação RGB e conexão Bluetooth/USB-C.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Descrição é obrigatória")
        @Size(max = 5000, message = "Descrição deve ter no máximo 5000 caracteres")
        String description,

        @Schema(
                description = "Grupo comercial amplo da categoria",
                example = "Periféricos",
                maxLength = 100,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Grupo da categoria é obrigatório")
        @Size(max = 100, message = "Grupo da categoria deve ter no máximo 100 caracteres")
        String categoryGroup,

        @Schema(
                description = "Categoria específica do produto",
                example = "Teclados",
                maxLength = 100,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Categoria é obrigatória")
        @Size(max = 100, message = "Categoria deve ter no máximo 100 caracteres")
        String category,

        @Schema(
                description = "Marca do produto",
                example = "PointClick",
                maxLength = 100,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Marca é obrigatória")
        @Size(max = 100, message = "Marca deve ter no máximo 100 caracteres")
        String brand,

        @Schema(
                description = "Modelo do produto",
                example = "PC Keys Pro 75",
                maxLength = 100,
                nullable = true
        )
        @Size(max = 100, message = "Modelo deve ter no máximo 100 caracteres")
        String model,

        @Schema(
                description = "Preço atual do produto. Esse preço pode mudar, mas pedidos fechados usam snapshot.",
                example = "399.90",
                minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Preço é obrigatório")
        @DecimalMin(value = "0.0", inclusive = true, message = "Preço não pode ser negativo")
        BigDecimal price,

        @Schema(
                description = """
                        Estoque físico total do produto.

                        A quantidade disponível para venda é calculada como:
                        stockQuantity - reservedQuantity.
                        """,
                example = "15",
                minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Quantidade em estoque é obrigatória")
        @Min(value = 0, message = "Estoque não pode ser negativo")
        Integer stockQuantity,

        @Schema(
                description = "URL ou caminho da imagem do produto retornado pelo upload",
                example = "/uploads/products/uuid-teclado.png",
                maxLength = 255,
                nullable = true
        )
        @Size(max = 255, message = "URL da imagem deve ter no máximo 255 caracteres")
        String imageUrl,

        @Schema(
                description = "Define se o produto nasce ativo no catálogo",
                example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Status ativo é obrigatório")
        Boolean active
) {
}