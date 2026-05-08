package com.pim.ecommerce.dto.response;

import com.pim.ecommerce.domain.entity.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = """
                Dados mínimos do usuário autenticado.

                Usado no retorno do login e na validação de sessão no frontend.
                Dados sensíveis como senha nunca são retornados.
                """
)
public record AuthenticatedUserResponse(

        @Schema(description = "ID do usuário autenticado", example = "2")
        Long id,

        @Schema(description = "Nome completo do usuário autenticado", example = "Ana Souza")
        String fullName,

        @Schema(description = "E-mail do usuário autenticado", example = "ana@email.com")
        String email,

        @Schema(description = "URL da foto/avatar do usuário autenticado", example = "/uploads/users/avatars/avatar-2.png", nullable = true)
        String avatarUrl,

        @Schema(description = "Perfil de acesso do usuário", example = "CUSTOMER")
        UserRole role
) {
}