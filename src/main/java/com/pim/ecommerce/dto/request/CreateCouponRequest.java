package com.pim.ecommerce.dto.request;

import com.pim.ecommerce.domain.entity.enums.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Dados para criação de cupom de desconto")
public record CreateCouponRequest(

        @Schema(
                description = "Código único do cupom. Será salvo em maiúsculas.",
                example = "POINT10",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Código do cupom é obrigatório")
        @Size(max = 50, message = "Código do cupom deve ter no máximo 50 caracteres")
        String code,

        @Schema(
                description = "Descrição administrativa do cupom",
                example = "Cupom de boas-vindas com 10% de desconto",
                nullable = true
        )
        @Size(max = 180, message = "Descrição deve ter no máximo 180 caracteres")
        String description,

        @Schema(
                description = "Tipo de desconto do cupom",
                example = "PERCENTAGE",
                allowableValues = {"PERCENTAGE", "FIXED_AMOUNT"},
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Tipo de desconto é obrigatório")
        DiscountType discountType,

        @Schema(
                description = """
                        Valor do desconto.

                        Para PERCENTAGE, representa porcentagem.
                        Exemplo: 10.00 = 10%.

                        Para FIXED_AMOUNT, representa valor em reais.
                        Exemplo: 25.00 = R$ 25,00.
                        """,
                example = "10.00",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Valor do desconto é obrigatório")
        @DecimalMin(value = "0.01", inclusive = true, message = "Valor do desconto deve ser maior que zero")
        BigDecimal discountValue,

        @Schema(
                description = "Valor mínimo dos itens do pedido para aplicar o cupom",
                example = "100.00",
                nullable = true
        )
        @DecimalMin(value = "0.0", inclusive = true, message = "Valor mínimo do pedido não pode ser negativo")
        BigDecimal minimumOrderValue,

        @Schema(
                description = "Define se o cupom nasce ativo",
                example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Status ativo é obrigatório")
        Boolean active,

        @Schema(
                description = "Data de início da validade do cupom",
                example = "2026-05-01T00:00:00",
                nullable = true
        )
        LocalDateTime startsAt,

        @Schema(
                description = "Data de fim da validade do cupom",
                example = "2026-05-31T23:59:59",
                nullable = true
        )
        LocalDateTime endsAt,

        @Schema(
                description = "Limite total de usos do cupom. Se null, não possui limite.",
                example = "100",
                nullable = true
        )
        Integer usageLimit
) {
}