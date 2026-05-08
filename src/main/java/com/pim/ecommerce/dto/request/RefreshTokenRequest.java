package com.pim.ecommerce.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para renovação de sessão usando refresh token")
public record RefreshTokenRequest(

        @Schema(
                description = "Refresh token retornado no login",
                example = "4e5d2c74-6f42-48d3-b3d7-8a1c5a4a9f90",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Refresh token é obrigatório")
        @Size(max = 255, message = "Refresh token deve ter no máximo 255 caracteres")
        String refreshToken
) {
}