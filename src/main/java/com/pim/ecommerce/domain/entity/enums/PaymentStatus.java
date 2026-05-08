package com.pim.ecommerce.domain.entity.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Status do pagamento simulado")
public enum PaymentStatus {

    @Schema(description = "Pagamento criado, mas ainda não confirmado")
    PENDING,

    @Schema(description = "Pagamento aprovado")
    APPROVED,

    @Schema(description = "Pagamento recusado ou com falha")
    FAILED,

    @Schema(description = "Pagamento cancelado")
    CANCELLED
}