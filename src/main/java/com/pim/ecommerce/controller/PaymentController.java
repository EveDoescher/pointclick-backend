package com.pim.ecommerce.controller;

import com.pim.ecommerce.dto.request.CreatePaymentRequest;
import com.pim.ecommerce.dto.response.ErrorResponse;
import com.pim.ecommerce.dto.response.PaymentResponse;
import com.pim.ecommerce.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Tag(
        name = "05 - Pagamentos",
        description = """
                Endpoints responsáveis pela simulação de pagamentos.

                Fluxo:
                1. Criar pagamento para pedido CLOSED;
                2. Se cartão, aprova automaticamente;
                3. Se PIX ou boleto, gera código/QR Code e URL de confirmação;
                4. A confirmação pode acontecer por nova aba, QR Code ou POST direto;
                5. O frontend pode fazer polling em GET /payments/orders/{orderId}.
                """
)
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "01 - Criar pagamento simulado",
            description = """
                    Cria pagamento para um pedido CLOSED.

                    CREDIT_CARD:
                    - valida cartão;
                    - aprova imediatamente;
                    - pedido vira PAID.

                    DEBIT_CARD:
                    - valida cartão;
                    - aprova imediatamente;
                    - pedido vira PAID.

                    PIX:
                    - gera pixCode;
                    - gera pixQrCodeBase64;
                    - gera pixConfirmationUrl;
                    - fica PENDING até confirmação.

                    BANK_SLIP:
                    - gera linha digitável;
                    - gera código de barras;
                    - gera bankSlipConfirmationUrl;
                    - fica PENDING até confirmação.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Pagamento criado com sucesso",
                    content = @Content(
                            schema = @Schema(implementation = PaymentResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "id": 10,
                                              "orderId": 7,
                                              "method": "PIX",
                                              "status": "PENDING",
                                              "amount": 734.82,
                                              "pixCode": "PIX-SIMULADO-A1B2C3D4",
                                              "pixQrCodeBase64": "data:image/png;base64,...",
                                              "pixConfirmationUrl": "http://localhost:8080/payments/10/pix-confirmation?token=..."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Erro de regra de negócio ou dados inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/orders/{orderId}")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(
            @Parameter(description = "ID do pedido que será pago", example = "7", required = true)
            @PathVariable Long orderId,

            @org.springframework.web.bind.annotation.RequestBody @Valid CreatePaymentRequest request
    ) {
        return paymentService.createPayment(orderId, request);
    }

    @Operation(
            summary = "02 - Consultar pagamento por pedido",
            description = """
                    Retorna o pagamento associado ao pedido.

                    Usado pelo frontend para:
                    - recuperar PIX ou boleto pendente;
                    - fazer polling automático da tela de pagamento;
                    - atualizar status após confirmação em outra aba.
                    """
    )
    @GetMapping("/orders/{orderId}")
    public PaymentResponse findByOrderId(
            @Parameter(description = "ID do pedido", example = "7", required = true)
            @PathVariable Long orderId
    ) {
        return paymentService.findByOrderId(orderId);
    }

    @Operation(
            summary = "03 - Cancelar pagamento",
            description = """
                    Cancela um pagamento pendente ou com falha.

                    Não cancela pagamento aprovado.
                    """
    )
    @PostMapping("/{paymentId}/cancel")
    public PaymentResponse cancelPayment(
            @Parameter(description = "ID do pagamento", example = "10", required = true)
            @PathVariable Long paymentId
    ) {
        return paymentService.cancelPayment(paymentId);
    }

    @Operation(
            summary = "04 - Abrir página de confirmação PIX",
            description = """
                    Página HTML intermediária para confirmação de PIX.

                    O QR Code abre esta URL via GET.
                    A página executa POST em /payments/{paymentId}/confirm-pix.
                    """
    )
    @GetMapping(value = "/{paymentId}/pix-confirmation", produces = "text/html")
    public String pixConfirmationPage(
            @Parameter(description = "ID do pagamento PIX pendente", example = "10", required = true)
            @PathVariable Long paymentId,

            @Parameter(description = "Token público de confirmação", required = true)
            @RequestParam String token
    ) {
        return """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Confirmando PIX</title>
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        background: #F7F3EC;
                        color: #18181b;
                        min-height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        margin: 0;
                        padding: 24px;
                    }
                    .card {
                        max-width: 460px;
                        width: 100%;
                        background: white;
                        border-radius: 24px;
                        padding: 32px;
                        box-shadow: 0 20px 60px rgba(0,0,0,.12);
                        text-align: center;
                    }
                    .badge {
                        display: inline-block;
                        padding: 8px 12px;
                        border-radius: 999px;
                        background: #ecfdf5;
                        color: #047857;
                        font-size: 12px;
                        font-weight: 800;
                        letter-spacing: .12em;
                        text-transform: uppercase;
                    }
                    h1 { font-size: 26px; margin: 18px 0 10px; }
                    p { color: #52525b; line-height: 1.6; }
                </style>
            </head>
            <body>
                <div class="card" id="card">
                    <span class="badge">PointClick</span>
                    <h1>Confirmando PIX...</h1>
                    <p>Aguarde, estamos confirmando seu pagamento simulado.</p>
                </div>

                <script>
                    fetch('/payments/__PAYMENT_ID__/confirm-pix?token=__TOKEN__', { method: 'POST' })
                        .then(response => {
                            const card = document.getElementById('card');

                            if (response.ok) {
                                card.innerHTML = '<span class="badge">Sucesso</span><h1>Pagamento PIX confirmado!</h1><p>Você já pode fechar esta página e voltar para a loja.</p>';
                            } else {
                                card.innerHTML = '<span class="badge">Erro</span><h1>Não foi possível confirmar</h1><p>Token inválido, expirado ou pagamento não encontrado.</p>';
                            }
                        })
                        .catch(() => {
                            document.getElementById('card').innerHTML = '<span class="badge">Erro</span><h1>Erro de conexão</h1><p>Não foi possível confirmar o pagamento PIX.</p>';
                        });
                </script>
            </body>
            </html>
            """
                .replace("__PAYMENT_ID__", paymentId.toString())
                .replace("__TOKEN__", token);
    }

    @Operation(
            summary = "05 - Confirmar pagamento PIX",
            description = """
                    Confirma manualmente um pagamento PIX pendente.

                    Ao confirmar:
                    - pagamento muda para APPROVED;
                    - pedido muda para PAID;
                    - estoque reservado é baixado definitivamente;
                    - notificação de pagamento aprovado é criada.
                    """
    )
    @PostMapping("/{paymentId}/confirm-pix")
    public PaymentResponse confirmPixPayment(
            @Parameter(description = "ID do pagamento PIX", example = "10", required = true)
            @PathVariable Long paymentId,

            @Parameter(description = "Token público de confirmação", required = true)
            @RequestParam String token
    ) {
        return paymentService.confirmPixPayment(paymentId, token);
    }

    @Operation(
            summary = "06 - Abrir página de confirmação de boleto",
            description = """
                    Página HTML intermediária para confirmação de boleto.

                    O código/URL abre esta página via GET.
                    A página executa POST em /payments/{paymentId}/confirm-bank-slip.
                    """
    )
    @GetMapping(value = "/{paymentId}/bank-slip-confirmation", produces = "text/html")
    public String bankSlipConfirmationPage(
            @Parameter(description = "ID do pagamento por boleto", example = "11", required = true)
            @PathVariable Long paymentId,

            @Parameter(description = "Token público de confirmação", required = true)
            @RequestParam String token
    ) {
        return """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Confirmando Boleto</title>
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        background: #F7F3EC;
                        color: #18181b;
                        min-height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        margin: 0;
                        padding: 24px;
                    }
                    .card {
                        max-width: 460px;
                        width: 100%;
                        background: white;
                        border-radius: 24px;
                        padding: 32px;
                        box-shadow: 0 20px 60px rgba(0,0,0,.12);
                        text-align: center;
                    }
                    .badge {
                        display: inline-block;
                        padding: 8px 12px;
                        border-radius: 999px;
                        background: #ecfdf5;
                        color: #047857;
                        font-size: 12px;
                        font-weight: 800;
                        letter-spacing: .12em;
                        text-transform: uppercase;
                    }
                    h1 { font-size: 26px; margin: 18px 0 10px; }
                    p { color: #52525b; line-height: 1.6; }
                </style>
            </head>
            <body>
                <div class="card" id="card">
                    <span class="badge">PointClick</span>
                    <h1>Confirmando boleto...</h1>
                    <p>Aguarde, estamos confirmando seu pagamento simulado.</p>
                </div>

                <script>
                    fetch('/payments/__PAYMENT_ID__/confirm-bank-slip?token=__TOKEN__', { method: 'POST' })
                        .then(response => {
                            const card = document.getElementById('card');

                            if (response.ok) {
                                card.innerHTML = '<span class="badge">Sucesso</span><h1>Pagamento do boleto confirmado!</h1><p>Você já pode fechar esta página e voltar para a loja.</p>';
                            } else {
                                card.innerHTML = '<span class="badge">Erro</span><h1>Não foi possível confirmar</h1><p>Token inválido, expirado ou pagamento não encontrado.</p>';
                            }
                        })
                        .catch(() => {
                            document.getElementById('card').innerHTML = '<span class="badge">Erro</span><h1>Erro de conexão</h1><p>Não foi possível confirmar o pagamento do boleto.</p>';
                        });
                </script>
            </body>
            </html>
            """
                .replace("__PAYMENT_ID__", paymentId.toString())
                .replace("__TOKEN__", token);
    }

    @Operation(
            summary = "07 - Confirmar pagamento de boleto",
            description = """
                    Confirma manualmente um pagamento por boleto pendente.

                    Ao confirmar:
                    - pagamento muda para APPROVED;
                    - pedido muda para PAID;
                    - estoque reservado é baixado definitivamente;
                    - notificação de pagamento aprovado é criada.
                    """
    )
    @PostMapping("/{paymentId}/confirm-bank-slip")
    public PaymentResponse confirmBankSlipPayment(
            @Parameter(description = "ID do pagamento por boleto", example = "11", required = true)
            @PathVariable Long paymentId,

            @Parameter(description = "Token público de confirmação", required = true)
            @RequestParam String token
    ) {
        return paymentService.confirmBankSlipPayment(paymentId, token);
    }
}