package com.pim.ecommerce.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(
        description = """
                Dados necessários para autenticação do usuário.

                O login retorna um access token JWT e um refresh token.
                O access token deve ser usado nas rotas protegidas.
                O refresh token deve ser usado para renovar a sessão.
                """
)
public record LoginRequest(

        @Schema(
                description = "E-mail cadastrado do usuário",
                example = "ana@email.com",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        @Size(max = 150, message = "E-mail deve ter no máximo 150 caracteres")
        String email,

        @Schema(
                description = "Senha do usuário",
                example = "123456",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Senha é obrigatória")
        @Size(max = 100, message = "Senha deve ter no máximo 100 caracteres")
        String password
) {
}