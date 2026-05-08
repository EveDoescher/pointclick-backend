package com.pim.ecommerce.dto.response;

import com.pim.ecommerce.domain.entity.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Resposta com os dados públicos do usuário")
public record UserResponse(

        @Schema(description = "ID do usuário", example = "2")
        Long id,

        @Schema(description = "Nome completo do usuário", example = "Ana Souza")
        String fullName,

        @Schema(description = "E-mail do usuário", example = "ana@email.com")
        String email,

        @Schema(description = "CPF do usuário", example = "12345678900", nullable = true)
        String cpf,

        @Schema(description = "Telefone do usuário", example = "(19) 99999-9999", nullable = true)
        String phone,

        @Schema(description = "URL da foto/avatar do usuário", example = "/uploads/users/avatars/avatar-2.png", nullable = true)
        String avatarUrl,

        @Schema(description = "Perfil de acesso do usuário", example = "CUSTOMER")
        UserRole role,

        @Schema(description = "Indica se o usuário está ativo", example = "true")
        Boolean active,

        @Schema(description = "Endereço principal vinculado ao usuário", nullable = true)
        AddressResponse address,

        @Schema(
                description = """
                        Indica se o usuário já possui os dados mínimos para finalizar uma compra.

                        Atualmente considera endereço cadastrado.
                        """
        )
        Boolean profileCompleteForCheckout,

        @Schema(description = "Data de criação do usuário", example = "2026-04-27T20:00:00")
        LocalDateTime createdAt,

        @Schema(description = "Data da última atualização do usuário", example = "2026-04-27T20:00:00")
        LocalDateTime updatedAt
) {
}