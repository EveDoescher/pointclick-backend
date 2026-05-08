package com.pim.ecommerce.controller;

import com.pim.ecommerce.dto.request.CreateCouponRequest;
import com.pim.ecommerce.dto.response.CouponResponse;
import com.pim.ecommerce.dto.response.ErrorResponse;
import com.pim.ecommerce.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
@Tag(
        name = "10 - Cupons",
        description = """
                Endpoints administrativos para gerenciamento de cupons de desconto.

                O uso do cupom no carrinho acontece em:
                - POST /orders/{orderId}/coupon;
                - DELETE /orders/{orderId}/coupon.
                """
)
public class CouponController {

    private final CouponService couponService;

    @Operation(
            summary = "01 - Criar cupom",
            description = """
                    Cria um novo cupom de desconto.

                    Tipos:
                    - PERCENTAGE: desconto percentual;
                    - FIXED_AMOUNT: desconto fixo em reais.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Cupom criado com sucesso",
                    content = @Content(schema = @Schema(implementation = CouponResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados inválidos ou código duplicado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CouponResponse create(
            @org.springframework.web.bind.annotation.RequestBody @Valid CreateCouponRequest request
    ) {
        return couponService.create(request);
    }

    @Operation(
            summary = "02 - Listar cupons",
            description = """
                    Lista cupons cadastrados.

                    Filtro opcional:
                    - active=true;
                    - active=false;
                    - sem filtro retorna todos.
                    """
    )
    @GetMapping
    public List<CouponResponse> findAll(
            @RequestParam(required = false) Boolean active
    ) {
        return couponService.findAll(active);
    }

    @Operation(
            summary = "03 - Buscar cupom por ID",
            description = "Retorna os dados completos de um cupom."
    )
    @GetMapping("/{id}")
    public CouponResponse findById(
            @PathVariable Long id
    ) {
        return couponService.findById(id);
    }

    @Operation(
            summary = "04 - Atualizar cupom",
            description = """
                    Atualiza os dados de um cupom.

                    Permite alterar:
                    - código;
                    - descrição;
                    - tipo de desconto;
                    - valor;
                    - valor mínimo;
                    - validade;
                    - limite de uso;
                    - status ativo.
                    """
    )
    @PutMapping("/{id}")
    public CouponResponse update(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestBody @Valid CreateCouponRequest request
    ) {
        return couponService.update(id, request);
    }

    @Operation(
            summary = "05 - Desativar cupom",
            description = "Desativa um cupom, impedindo novo uso."
    )
    @PatchMapping("/{id}/deactivate")
    public CouponResponse deactivate(
            @PathVariable Long id
    ) {
        return couponService.deactivate(id);
    }

    @Operation(
            summary = "06 - Reativar cupom",
            description = "Reativa um cupom desativado."
    )
    @PatchMapping("/{id}/activate")
    public CouponResponse activate(
            @PathVariable Long id
    ) {
        return couponService.activate(id);
    }
}