package com.pim.ecommerce.dto.request;

import com.pim.ecommerce.domain.entity.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para criação de um pagamento simulado")
public record CreatePaymentRequest(

        @Schema(
                description = """
                        Forma de pagamento desejada.

                        Regras:
                        - CREDIT_CARD aprova imediatamente se os dados do cartão estiverem válidos;
                        - DEBIT_CARD aprova imediatamente se os dados do cartão estiverem válidos;
                        - PIX gera QR Code e fica pendente até confirmação;
                        - BANK_SLIP gera código de barras e fica pendente até confirmação.
                        """,
                example = "PIX",
                allowableValues = {"PIX", "CREDIT_CARD", "DEBIT_CARD", "BANK_SLIP"},
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Forma de pagamento é obrigatória")
        PaymentMethod paymentMethod,

        @Schema(
                description = "Número do cartão. Obrigatório para CREDIT_CARD e DEBIT_CARD. Pode conter espaços.",
                example = "4111111111111111",
                nullable = true
        )
        @Size(max = 19, message = "Número do cartão inválido")
        String cardNumber,

        @Schema(
                description = "Nome do titular do cartão. Obrigatório para CREDIT_CARD e DEBIT_CARD.",
                example = "ANA SOUZA",
                nullable = true
        )
        @Size(max = 120, message = "Nome do titular inválido")
        String cardHolderName,

        @Schema(
                description = "Data de expiração do cartão no formato MM/AA. Obrigatório para CREDIT_CARD e DEBIT_CARD.",
                example = "12/30",
                nullable = true
        )
        @Size(max = 5, message = "Data de expiração inválida")
        String expirationDate,

        @Schema(
                description = "Código de segurança do cartão. Deve conter 3 ou 4 dígitos.",
                example = "123",
                nullable = true
        )
        @Size(max = 4, message = "CVV inválido")
        String cvv,

        @Schema(
                description = "Número de parcelas. Obrigatório apenas para CREDIT_CARD. Deve estar entre 1 e 12.",
                example = "3",
                nullable = true
        )
        @Min(value = 1, message = "Parcelas devem ser no mínimo 1")
        @Max(value = 12, message = "Parcelas devem ser no máximo 12")
        Integer installments,

        @Schema(
                description = "Observação ou instrução de entrega associada ao pagamento/pedido.",
                example = "Entregar em horário comercial",
                nullable = true
        )
        @Size(max = 255, message = "Observação deve ter no máximo 255 caracteres")
        String notes
) {
}