package com.pim.ecommerce.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(
        description = """
                Dados necessários para cadastro inicial de usuário.

                Este cadastro é propositalmente simples para melhorar a experiência do usuário.
                Dados como CPF, telefone e endereço podem ser preenchidos depois, na tela Minha Conta
                ou durante o checkout.
                """
)
public record CreateUserRequest(

        @Schema(
                description = "Nome completo do usuário",
                example = "Ana Souza",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Nome completo é obrigatório")
        @Size(min = 3, max = 120, message = "Nome completo deve ter entre 3 e 120 caracteres")
        String fullName,

        @Schema(
                description = "E-mail único do usuário",
                example = "ana@email.com",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        @Size(max = 150, message = "E-mail deve ter no máximo 150 caracteres")
        String email,

        @Schema(
                description = "Senha do usuário. Será armazenada com hash.",
                example = "123456",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 6, max = 100, message = "Senha deve ter entre 6 e 100 caracteres")
        String password
) {
}