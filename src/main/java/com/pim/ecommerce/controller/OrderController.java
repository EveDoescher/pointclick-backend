package com.pim.ecommerce.controller;

import com.pim.ecommerce.domain.entity.enums.OrderStatus;
import com.pim.ecommerce.dto.request.AddOrderItemRequest;
import com.pim.ecommerce.dto.request.ApplyCouponRequest;
import com.pim.ecommerce.dto.request.CreateOrderRequest;
import com.pim.ecommerce.dto.request.ShippingQuoteRequest;
import com.pim.ecommerce.dto.request.UpdateOrderItemRequest;
import com.pim.ecommerce.dto.response.AdminOrderSummaryResponse;
import com.pim.ecommerce.dto.response.ErrorResponse;
import com.pim.ecommerce.dto.response.OrderResponse;
import com.pim.ecommerce.dto.response.OrderStatusSummaryResponse;
import com.pim.ecommerce.dto.response.OrderSummaryResponse;
import com.pim.ecommerce.dto.response.ShippingQuoteResponse;
import com.pim.ecommerce.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Tag(
        name = "04 - Pedidos",
        description = """
                Endpoints responsáveis pelo fluxo de carrinho, pedido, entrega e confirmação de recebimento.

                Fluxo principal:
                1. Criar pedido;
                2. Adicionar itens;
                3. Simular frete;
                4. Aplicar cupom, se houver;
                5. Fechar pedido;
                6. Criar pagamento em /payments/orders/{orderId};
                7. Admin marca como enviado;
                8. Admin marca como entregue;
                9. Cliente confirma recebimento.
                """
)
public class OrderController {

    private final OrderService orderService;

    @Operation(
            summary = "01 - Criar novo pedido",
            description = """
                    Cria um novo pedido/carrinho para um usuário ativo.

                    O pedido nasce como PENDING.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Pedido criado com sucesso",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Usuário inválido ou inativo",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(
            @org.springframework.web.bind.annotation.RequestBody @Valid CreateOrderRequest request
    ) {
        return orderService.create(request);
    }

    @Operation(
            summary = "02 - Buscar carrinho atual do usuário logado",
            description = """
                    Busca o pedido aberto do usuário autenticado com status PENDING.

                    Se não existir carrinho aberto, retorna 204 No Content.
                    """
    )
    @GetMapping("/current")
    public ResponseEntity<OrderResponse> findCurrentCart() {
        OrderResponse currentCart = orderService.findCurrentCart();

        if (currentCart == null) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(currentCart);
    }

    @Operation(
            summary = "03 - Listar meus pedidos",
            description = "Retorna o histórico de pedidos do usuário autenticado."
    )
    @GetMapping("/my")
    public List<OrderSummaryResponse> findMyOrders() {
        return orderService.findMyOrders();
    }

    @Operation(
            summary = "04 - Listar pedidos para admin",
            description = """
                    Lista pedidos para painel administrativo.

                    Filtro opcional por status:
                    PENDING, CLOSED, PAID, SHIPPED, DELIVERED, FINISHED ou CANCELLED.
                    """
    )
    @GetMapping
    public List<AdminOrderSummaryResponse> findAllForAdmin(
            @RequestParam(required = false) OrderStatus status
    ) {
        return orderService.findAllForAdmin(status);
    }

    @Operation(
            summary = "05 - Buscar resumo administrativo de pedidos",
            description = "Retorna a quantidade de pedidos agrupada por status."
    )
    @GetMapping("/summary")
    public OrderStatusSummaryResponse getStatusSummary() {
        return orderService.getStatusSummary();
    }

    @Operation(
            summary = "06 - Adicionar item ao pedido",
            description = """
                    Adiciona um produto ao pedido.

                    Regras:
                    - pedido precisa estar PENDING;
                    - produto precisa estar ativo;
                    - quantidade precisa respeitar o estoque disponível.
                    """
    )
    @PostMapping("/{orderId}/items")
    public OrderResponse addItem(
            @Parameter(description = "ID do pedido", example = "12", required = true)
            @PathVariable Long orderId,

            @org.springframework.web.bind.annotation.RequestBody @Valid AddOrderItemRequest request
    ) {
        return orderService.addItem(orderId, request);
    }

    @Operation(
            summary = "07 - Atualizar quantidade de item no carrinho",
            description = """
                    Atualiza a quantidade de um item do pedido.

                    Regras:
                    - pedido precisa estar PENDING;
                    - usuário precisa ser dono do pedido ou ADMIN.
                    """
    )
    @PutMapping("/{orderId}/items/{itemId}")
    public OrderResponse updateItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @org.springframework.web.bind.annotation.RequestBody @Valid UpdateOrderItemRequest request
    ) {
        return orderService.updateItem(orderId, itemId, request);
    }

    @Operation(
            summary = "08 - Remover item do carrinho",
            description = """
                    Remove um item do pedido.

                    Regras:
                    - pedido precisa estar PENDING;
                    - usuário precisa ser dono do pedido ou ADMIN.
                    """
    )
    @DeleteMapping("/{orderId}/items/{itemId}")
    public OrderResponse removeItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId
    ) {
        return orderService.removeItem(orderId, itemId);
    }

    @Operation(
            summary = "09 - Buscar pedido por ID",
            description = """
                    Retorna os detalhes completos de um pedido.

                    Inclui itens com imagem, marca, categoria e preço congelado no momento do pedido.
                    """
    )
    @GetMapping("/{orderId}")
    public OrderResponse findById(
            @Parameter(description = "ID do pedido", example = "12", required = true)
            @PathVariable Long orderId
    ) {
        return orderService.findById(orderId);
    }

    @Operation(
            summary = "10 - Buscar pedido por ID para admin",
            description = "Busca um pedido por ID para tela administrativa de detalhes."
    )
    @GetMapping("/admin/{orderId}")
    public OrderResponse findByIdForAdmin(
            @Parameter(description = "ID do pedido", example = "12", required = true)
            @PathVariable Long orderId
    ) {
        return orderService.findByIdForAdmin(orderId);
    }

    @Operation(
            summary = "11 - Simular frete do pedido",
            description = """
                    Simula o frete antes do fechamento do pedido.

                    O frete oficial será recalculado no fechamento.
                    """
    )
    @PostMapping("/{orderId}/shipping/quote")
    public ShippingQuoteResponse quoteShipping(
            @PathVariable Long orderId,
            @org.springframework.web.bind.annotation.RequestBody @Valid ShippingQuoteRequest request
    ) {
        return orderService.quoteShipping(orderId, request);
    }

    @Operation(
            summary = "12 - Aplicar cupom no pedido",
            description = """
                    Aplica um cupom ao pedido/carrinho.

                    Regras:
                    - pedido precisa estar PENDING;
                    - pedido precisa ter itens;
                    - cupom precisa estar ativo, válido e respeitar valor mínimo.
                    """
    )
    @PostMapping("/{orderId}/coupon")
    public OrderResponse applyCoupon(
            @PathVariable Long orderId,
            @org.springframework.web.bind.annotation.RequestBody @Valid ApplyCouponRequest request
    ) {
        return orderService.applyCoupon(orderId, request);
    }

    @Operation(
            summary = "13 - Remover cupom do pedido",
            description = """
                    Remove o cupom aplicado ao pedido.

                    Regras:
                    - pedido precisa estar PENDING.
                    """
    )
    @DeleteMapping("/{orderId}/coupon")
    public OrderResponse removeCoupon(
            @PathVariable Long orderId
    ) {
        return orderService.removeCoupon(orderId);
    }

    @Operation(
            summary = "14 - Fechar pedido",
            description = """
                    Fecha o pedido e transforma o carrinho em pedido reservado.

                    Ao fechar:
                    - status muda de PENDING para CLOSED;
                    - endereço é congelado;
                    - preço dos itens é congelado;
                    - frete é calculado;
                    - cupom/desconto é aplicado;
                    - estoque é reservado;
                    - reservationExpiresAt é definido.
                    """
    )
    @PostMapping("/{orderId}/close")
    public OrderResponse close(
            @Parameter(description = "ID do pedido", example = "12", required = true)
            @PathVariable Long orderId
    ) {
        return orderService.closeOrder(orderId);
    }

    @Operation(
            summary = "15 - Enviar pedido",
            description = """
                    Altera o pedido de PAID para SHIPPED.

                    Uso administrativo.
                    """
    )
    @PostMapping("/{orderId}/ship")
    public OrderResponse ship(
            @Parameter(description = "ID do pedido", example = "12", required = true)
            @PathVariable Long orderId
    ) {
        return orderService.shipOrder(orderId);
    }

    @Operation(
            summary = "16 - Marcar pedido como entregue",
            description = """
                    Altera o pedido de SHIPPED para DELIVERED.

                    Uso administrativo.
                    Depois disso, o cliente deve confirmar recebimento.
                    """
    )
    @PostMapping("/{orderId}/deliver")
    public OrderResponse deliver(
            @Parameter(description = "ID do pedido", example = "12", required = true)
            @PathVariable Long orderId
    ) {
        return orderService.deliverOrder(orderId);
    }

    @Operation(
            summary = "17 - Confirmar recebimento pelo cliente",
            description = """
                    Altera o pedido de DELIVERED para FINISHED.

                    Regra:
                    - apenas o dono do pedido pode confirmar recebimento.
                    """
    )
    @PostMapping("/{orderId}/confirm-delivery")
    public OrderResponse confirmDelivery(
            @Parameter(description = "ID do pedido", example = "12", required = true)
            @PathVariable Long orderId
    ) {
        return orderService.confirmDelivery(orderId);
    }

    @Operation(
            summary = "18 - Finalizar pedido administrativamente",
            description = """
                    Compatibilidade administrativa.

                    Altera o pedido de DELIVERED para FINISHED.
                    O fluxo preferencial é o cliente confirmar recebimento em /confirm-delivery.
                    """
    )
    @PostMapping("/{orderId}/finish")
    public OrderResponse finish(
            @Parameter(description = "ID do pedido", example = "12", required = true)
            @PathVariable Long orderId
    ) {
        return orderService.finishOrder(orderId);
    }

    @Operation(
            summary = "19 - Cancelar pedido",
            description = """
                    Cancela um pedido.

                    Regras:
                    - pode cancelar PENDING ou CLOSED;
                    - se estiver CLOSED, desfaz reserva de estoque;
                    - não cancela pedidos pagos/enviados/entregues/finalizados.
                    """
    )
    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancel(
            @Parameter(description = "ID do pedido", example = "12", required = true)
            @PathVariable Long orderId
    ) {
        return orderService.cancelOrder(orderId);
    }

    @Operation(
            summary = "20 - Reabrir pedido com reserva expirada",
            description = """
                    Reabre um pedido CLOSED cuja reserva expirou.

                    Uso administrativo.
                    """
    )
    @PostMapping("/{orderId}/reopen-expired")
    public OrderResponse reopenExpired(
            @Parameter(description = "ID do pedido", example = "12", required = true)
            @PathVariable Long orderId
    ) {
        return orderService.reopenExpiredOrder(orderId);
    }
}