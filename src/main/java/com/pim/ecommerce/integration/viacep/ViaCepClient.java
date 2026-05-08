package com.pim.ecommerce.integration.viacep;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class ViaCepClient {

    private final RestClient restClient;

    public ViaCepResponse getAddressByCep(String cep) {
        String normalizedCep = normalizeCep(cep);

        ViaCepResponse response = restClient.get()
                .uri("https://viacep.com.br/ws/{cep}/json/", normalizedCep)
                .retrieve()
                .body(ViaCepResponse.class);

        if (response == null || Boolean.TRUE.equals(response.erro())) {
            throw new IllegalArgumentException("CEP inválido ou não encontrado");
        }

        return response;
    }

    public ViaCepResponse findByCep(String cep) {
        return getAddressByCep(cep);
    }

    private String normalizeCep(String cep) {
        if (cep == null || cep.isBlank()) {
            throw new IllegalArgumentException("CEP não informado");
        }

        String normalizedCep = cep.replaceAll("\\D", "");

        if (normalizedCep.length() != 8) {
            throw new IllegalArgumentException("CEP deve conter 8 dígitos");
        }

        return normalizedCep;
    }
}