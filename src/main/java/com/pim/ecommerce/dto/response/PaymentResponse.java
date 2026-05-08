package com.pim.ecommerce.dto.response;

import com.pim.ecommerce.domain.entity.enums.PaymentMethod;
import com.pim.ecommerce.domain.entity.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Resposta com os dados do pagamento simulado")
public record PaymentResponse(

        @Schema(description = "Identificador do pagamento", example = "10")
        Long id,

        @Schema(description = "Identificador do pedido relacionado ao pagamento", example = "7")
        Long orderId,

        @Schema(description = "Forma de pagamento utilizada", example = "PIX")
        PaymentMethod method,

        @Schema(
                description = "Status atual do pagamento",
                example = "PENDING",
                allowableValues = {"PENDING", "APPROVED", "FAILED", "CANCELLED"}
        )
        PaymentStatus status,

        @Schema(description = "Valor total pago ou a pagar", example = "4614.90")
        BigDecimal amount,

        @Schema(description = "Número de parcelas, usado apenas para cartão de crédito", example = "3", nullable = true)
        Integer installments,

        @Schema(description = "Últimos 4 dígitos do cartão, quando aplicável", example = "1111", nullable = true)
        String cardLastFourDigits,

        @Schema(description = "Código PIX simulado", example = "PIX-SIMULADO-A1B2C3D4", nullable = true)
        String pixCode,

        @Schema(description = "Imagem do QR Code PIX em Base64", nullable = true)
        String pixQrCodeBase64,

        @Schema(description = "URL que confirma o pagamento PIX simulado", example = "http://localhost:8080/payments/10/pix-confirmation", nullable = true)
        String pixConfirmationUrl,

        @Schema(description = "Código de barras do boleto simulado", nullable = true)
        String bankSlipBarCode,

        @Schema(description = "Imagem do código de barras do boleto em Base64", nullable = true)
        String bankSlipBarCodeBase64,

        @Schema(description = "URL que confirma o pagamento do boleto simulado", example = "http://localhost:8080/payments/11/bank-slip-confirmation", nullable = true)
        String bankSlipConfirmationUrl,

        @Schema(description = "Linha digitável do boleto simulado", example = "34191.79001 01043.510047 91020.150008 5 00000000011", nullable = true)
        String digitableLine,

        @Schema(description = "Observações ou instruções associadas ao pagamento", example = "Entregar em horário comercial", nullable = true)
        String notes,

        @Schema(description = "Data de criação do pagamento", example = "2026-04-27T20:45:00")
        LocalDateTime createdAt,

        @Schema(description = "Data de confirmação do pagamento", example = "2026-04-27T20:48:00", nullable = true)
        LocalDateTime confirmedAt,

        @Schema(description = "Data de cancelamento do pagamento", example = "2026-04-27T20:50:00", nullable = true)
        LocalDateTime cancelledAt
) {
}