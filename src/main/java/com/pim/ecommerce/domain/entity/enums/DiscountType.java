package com.pim.ecommerce.domain.entity.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tipo de desconto aplicado por cupom")
public enum DiscountType {

    @Schema(description = "Desconto percentual sobre o valor dos itens")
    PERCENTAGE,

    @Schema(description = "Desconto fixo em reais")
    FIXED_AMOUNT
}