package com.pim.ecommerce.service;

import com.pim.ecommerce.integration.viacep.ViaCepClient;
import com.pim.ecommerce.integration.viacep.ViaCepResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FreightService {

    private static final Set<String> SUDESTE = Set.of("SP", "RJ", "MG", "ES");
    private static final Set<String> SUL = Set.of("PR", "SC", "RS");
    private static final Set<String> CENTRO_OESTE = Set.of("MS", "MT", "GO", "DF");
    private static final Set<String> NORDESTE = Set.of("BA", "SE", "AL", "PE", "PB", "RN", "CE", "PI", "MA");
    private static final Set<String> NORTE = Set.of("AC", "AP", "AM", "PA", "RO", "RR", "TO");

    @Value("${shipping.origin-cep}")
    private String shippingOriginCep;

    private final ViaCepClient viaCepClient;

    public BigDecimal calculateFreightByDestinationCep(String destinationCep) {
        ViaCepResponse origin = viaCepClient.getAddressByCep(shippingOriginCep);
        ViaCepResponse destination = viaCepClient.getAddressByCep(destinationCep);

        String originUf = normalizeUf(origin.uf());
        String destinationUf = normalizeUf(destination.uf());

        if (originUf.equals(destinationUf)) {
            return BigDecimal.valueOf(15.00);
        }

        if (SUDESTE.contains(destinationUf)) {
            return BigDecimal.valueOf(20.00);
        }

        if (SUL.contains(destinationUf) || CENTRO_OESTE.contains(destinationUf)) {
            return BigDecimal.valueOf(25.00);
        }

        if (NORTE.contains(destinationUf) || NORDESTE.contains(destinationUf)) {
            return BigDecimal.valueOf(35.00);
        }

        return BigDecimal.valueOf(30.00);
    }

    private String normalizeUf(String uf) {
        if (uf == null || uf.isBlank()) {
            throw new IllegalArgumentException("UF inválida para cálculo de frete");
        }

        return uf.trim().toUpperCase();
    }
}