package com.pim.ecommerce.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(
        description = """
                Dados para atualização completa de usuário.

                Observação:
                Este DTO é usado para atualização administrativa ou atualização completa.
                Para a tela Minha Conta, prefira PATCH /users/me/profile com UpdateMyProfileRequest.
                Este DTO não altera senha.
                """
)
public record UpdateUserRequest(

        @Schema(
                description = "Nome completo atualizado",
                example = "Ana Souza Silva",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Nome completo é obrigatório")
        @Size(min = 3, max = 120, message = "Nome completo deve ter entre 3 e 120 caracteres")
        String fullName,

        @Schema(
                description = "E-mail atualizado e único",
                example = "ana.silva@email.com",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        @Size(max = 150, message = "E-mail deve ter no máximo 150 caracteres")
        String email,

        @Schema(
                description = "CPF atualizado e único. Pode conter máscara.",
                example = "123.456.789-00",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "CPF é obrigatório")
        @Size(min = 11, max = 14, message = "CPF deve ter entre 11 e 14 caracteres")
        @Pattern(
                regexp = "^\\d{11}$|^\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}$",
                message = "CPF inválido"
        )
        String cpf,

        @Schema(
                description = "Telefone atualizado",
                example = "(19) 98888-7777",
                nullable = true
        )
        @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres")
        String phone,

        @Schema(
                description = "Endereço atualizado do usuário",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Endereço é obrigatório")
        @Valid
        UpdateAddressRequest address
) {
}