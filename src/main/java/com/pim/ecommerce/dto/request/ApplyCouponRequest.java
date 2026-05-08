package com.pim.ecommerce.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para aplicar cupom em um pedido")
public record ApplyCouponRequest(

        @Schema(
                description = "Código do cupom que será aplicado ao pedido",
                example = "POINT10",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Código do cupom é obrigatório")
        @Size(max = 50, message = "Código do cupom deve ter no máximo 50 caracteres")
        String code
) {
}