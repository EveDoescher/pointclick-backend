package com.pim.ecommerce.service;

import com.pim.ecommerce.dto.response.CepAddressResponse;
import com.pim.ecommerce.dto.response.ShippingQuoteResponse;
import com.pim.ecommerce.integration.viacep.ViaCepClient;
import com.pim.ecommerce.integration.viacep.ViaCepResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ShippingService {

    private static final Set<String> SUDESTE = Set.of("SP", "RJ", "MG", "ES");
    private static final Set<String> SUL = Set.of("PR", "SC", "RS");
    private static final Set<String> CENTRO_OESTE = Set.of("MS", "MT", "GO", "DF");

    private final FreightService freightService;
    private final ViaCepClient viaCepClient;

    public CepAddressResponse findAddressByCep(String cep) {
        ViaCepResponse viaCepResponse = viaCepClient.findByCep(cep);

        return new CepAddressResponse(
                normalizeCep(viaCepResponse.cep()),
                viaCepResponse.logradouro(),
                viaCepResponse.bairro(),
                viaCepResponse.localidade(),
                viaCepResponse.uf()
        );
    }

    public ShippingQuoteResponse quoteByCep(String cep) {
        ViaCepResponse viaCepResponse = viaCepClient.findByCep(cep);
        BigDecimal shippingPrice = freightService.calculateFreightByDestinationCep(cep);

        return new ShippingQuoteResponse(
                normalizeCep(viaCepResponse.cep()),
                shippingPrice,
                calculateEstimatedDays(viaCepResponse.uf()),
                viaCepResponse.logradouro(),
                viaCepResponse.bairro(),
                viaCepResponse.localidade(),
                viaCepResponse.uf()
        );
    }

    private Integer calculateEstimatedDays(String uf) {
        String normalizedUf = normalizeUf(uf);

        if ("SP".equals(normalizedUf)) {
            return 3;
        }

        if (SUDESTE.contains(normalizedUf)) {
            return 5;
        }

        if (SUL.contains(normalizedUf) || CENTRO_OESTE.contains(normalizedUf)) {
            return 7;
        }

        return 10;
    }

    private String normalizeCep(String cep) {
        if (cep == null) {
            return null;
        }

        return cep.replaceAll("\\D", "");
    }

    private String normalizeUf(String uf) {
        if (uf == null || uf.isBlank()) {
            throw new IllegalArgumentException("UF inválida");
        }

        return uf.trim().toUpperCase();
    }
}