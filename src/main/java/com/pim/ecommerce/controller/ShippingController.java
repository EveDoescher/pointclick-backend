package com.pim.ecommerce.controller;

import com.pim.ecommerce.dto.response.CepAddressResponse;
import com.pim.ecommerce.dto.response.ShippingQuoteResponse;
import com.pim.ecommerce.service.ShippingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shipping")
@RequiredArgsConstructor
@Tag(
        name = "06 - Frete e CEP",
        description = """
                Endpoints de apoio para consulta de CEP e simulação de frete.

                Usados no cadastro, carrinho, checkout e Minha Conta.
                """
)
public class ShippingController {

    private final ShippingService shippingService;

    @Operation(
            summary = "01 - Consultar endereço por CEP",
            description = """
                    Consulta dados de endereço a partir de um CEP.

                    Centraliza a integração com ViaCEP no backend.
                    O CEP pode ser enviado com ou sem máscara.
                    """
    )
    @GetMapping("/cep/{cep}")
    public CepAddressResponse findAddressByCep(@PathVariable String cep) {
        return shippingService.findAddressByCep(cep);
    }

    @Operation(
            summary = "02 - Simular frete por CEP",
            description = """
                    Simula o frete usando apenas o CEP.

                    Útil para a tela de carrinho antes do fechamento oficial do pedido.
                    O fechamento do pedido recalcula o frete usando o endereço cadastrado.
                    """
    )
    @GetMapping("/quote")
    public ShippingQuoteResponse quoteByCep(
            @RequestParam String cep
    ) {
        return shippingService.quoteByCep(cep);
    }
}