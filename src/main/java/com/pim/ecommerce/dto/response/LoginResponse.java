package com.pim.ecommerce.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resposta retornada após autenticação bem-sucedida")
public record LoginResponse(

        @Schema(
                description = "Access token JWT que deve ser enviado no header Authorization",
                example = "eyJhbGciOiJIUzI1NiJ9..."
        )
        String token,

        @Schema(
                description = "Tipo do token",
                example = "Bearer"
        )
        String type,

        @Schema(
                description = "Refresh token usado para renovar a sessão",
                example = "4e5d2c74-6f42-48d3-b3d7-8a1c5a4a9f90"
        )
        String refreshToken,

        @Schema(
                description = "Tempo de expiração do access token em milissegundos",
                example = "3600000"
        )
        Long expiresIn,

        @Schema(
                description = "Dados mínimos do usuário autenticado"
        )
        AuthenticatedUserResponse user
) {
}