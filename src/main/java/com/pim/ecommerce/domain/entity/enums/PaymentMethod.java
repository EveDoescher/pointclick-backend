package com.pim.ecommerce.domain.entity.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Forma de pagamento escolhida pelo cliente")
public enum PaymentMethod {

    @Schema(description = "Pagamento via PIX simulado")
    PIX,

    @Schema(description = "Pagamento por cartão de crédito simulado")
    CREDIT_CARD,

    @Schema(description = "Pagamento por cartão de débito simulado")
    DEBIT_CARD,

    @Schema(description = "Pagamento via boleto bancário simulado")
    BANK_SLIP
}