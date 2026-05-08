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
                Dados para atualização de produto.

                A atualização pode alterar dados comerciais, estoque, imagem e status ativo.
                Alterações de preço não afetam pedidos já fechados, pois pedidos usam snapshot de preço.
                Se o preço for reduzido, usuários que favoritaram o produto podem ser notificados.
                """
)
public record UpdateProductRequest(

        @Schema(
                description = "Nome comercial atualizado do produto",
                example = "Teclado Mecânico Premium RGB Wireless",
                maxLength = 150,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 150, message = "Nome deve ter no máximo 150 caracteres")
        String name,

        @Schema(
                description = "Descrição atualizada do produto",
                example = "Teclado mecânico premium sem fio com iluminação RGB, conexão Bluetooth/USB-C e bateria de longa duração.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Descrição é obrigatória")
        @Size(max = 5000, message = "Descrição deve ter no máximo 5000 caracteres")
        String description,

        @Schema(
                description = "Grupo comercial amplo atualizado da categoria",
                example = "Periféricos",
                maxLength = 100,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Grupo da categoria é obrigatório")
        @Size(max = 100, message = "Grupo da categoria deve ter no máximo 100 caracteres")
        String categoryGroup,

        @Schema(
                description = "Categoria atualizada do produto",
                example = "Teclado",
                maxLength = 100,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Categoria é obrigatória")
        @Size(max = 100, message = "Categoria deve ter no máximo 100 caracteres")
        String category,

        @Schema(
                description = "Marca atualizada do produto",
                example = "PointClick",
                maxLength = 100,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Marca é obrigatória")
        @Size(max = 100, message = "Marca deve ter no máximo 100 caracteres")
        String brand,

        @Schema(
                description = "Modelo atualizado do produto",
                example = "PC Keys Pro 75",
                maxLength = 100,
                nullable = true
        )
        @Size(max = 100, message = "Modelo deve ter no máximo 100 caracteres")
        String model,

        @Schema(
                description = "Preço atualizado do produto",
                example = "349.90",
                minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Preço é obrigatório")
        @DecimalMin(value = "0.0", inclusive = true, message = "Preço não pode ser negativo")
        BigDecimal price,

        @Schema(
                description = "Estoque físico total atualizado do produto",
                example = "25",
                minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Quantidade em estoque é obrigatória")
        @Min(value = 0, message = "Estoque não pode ser negativo")
        Integer stockQuantity,

        @Schema(
                description = "URL ou caminho atualizado da imagem do produto retornado pelo upload",
                example = "/uploads/products/uuid-teclado.png",
                maxLength = 255,
                nullable = true
        )
        @Size(max = 255, message = "URL da imagem deve ter no máximo 255 caracteres")
        String imageUrl,

        @Schema(
                description = """
                        Status ativo do produto.

                        true: produto aparece na vitrine pública;
                        false: produto fica oculto da vitrine, mas continua no histórico e no admin.
                        """,
                example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Status ativo é obrigatório")
        Boolean active
) {
}