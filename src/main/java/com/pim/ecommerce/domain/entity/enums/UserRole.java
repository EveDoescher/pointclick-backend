package com.pim.ecommerce.domain.entity.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Perfil de acesso do usuário no sistema")
public enum UserRole {

    @Schema(description = "Cliente comum do e-commerce")
    CUSTOMER,

    @Schema(description = "Administrador do e-commerce")
    ADMIN
}