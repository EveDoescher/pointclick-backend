package com.pim.ecommerce.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(
        description = """
                Dados para atualização parcial do perfil do usuário logado.

                Este DTO é usado na tela Minha Conta.
                Ele não altera e-mail, senha ou endereço.
                O endereço continua sendo atualizado por PUT /users/me/address.
                """
)
public record UpdateMyProfileRequest(

        @Schema(
                description = "Nome completo atualizado. Se não enviado, mantém o nome atual.",
                example = "Ana Souza Silva",
                nullable = true
        )
        @Size(min = 3, max = 120, message = "Nome completo deve ter entre 3 e 120 caracteres")
        String fullName,

        @Schema(
                description = "CPF do usuário. Pode conter máscara. Se não enviado, mantém o CPF atual.",
                example = "123.456.789-00",
                nullable = true
        )
        @Size(min = 11, max = 14, message = "CPF deve ter entre 11 e 14 caracteres")
        @Pattern(
                regexp = "^$|^\\d{11}$|^\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}$",
                message = "CPF inválido"
        )
        String cpf,

        @Schema(
                description = "Telefone do usuário. Se não enviado, mantém o telefone atual.",
                example = "(19) 99999-9999",
                nullable = true
        )
        @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres")
        String phone
) {
}