package com.pim.ecommerce.service;

import com.pim.ecommerce.integration.viacep.ViaCepClient;
import com.pim.ecommerce.integration.viacep.ViaCepResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShippingServiceTest {

    @Mock
    private FreightService freightService;

    @Mock
    private ViaCepClient viaCepClient;

    @InjectMocks
    private ShippingService shippingService;

    @Test
    void shouldFindAddressByCep() {
        when(viaCepClient.findByCep("13480-370"))
                .thenReturn(new ViaCepResponse("13480-370", "Rua das Flores", "", "Centro", "Limeira", "SP", false));

        var response = shippingService.findAddressByCep("13480-370");

        assertThat(response.cep()).isEqualTo("13480370");
        assertThat(response.street()).isEqualTo("Rua das Flores");
        assertThat(response.city()).isEqualTo("Limeira");
        assertThat(response.state()).isEqualTo("SP");
    }

    @Test
    void shouldQuoteShippingForSaoPauloWithThreeDays() {
        when(viaCepClient.findByCep("13480-370"))
                .thenReturn(new ViaCepResponse("13480-370", "Rua das Flores", "", "Centro", "Limeira", "SP", false));
        when(freightService.calculateFreightByDestinationCep("13480-370"))
                .thenReturn(new BigDecimal("15.00"));

        var response = shippingService.quoteByCep("13480-370");

        assertThat(response.shippingPrice()).isEqualByComparingTo("15.00");
        assertThat(response.estimatedDays()).isEqualTo(3);
        assertThat(response.cep()).isEqualTo("13480370");
    }

    @Test
    void shouldQuoteShippingForSulWithSevenDays() {
        when(viaCepClient.findByCep("80010-000"))
                .thenReturn(new ViaCepResponse("80010-000", "Rua", "", "Centro", "Curitiba", "PR", false));
        when(freightService.calculateFreightByDestinationCep("80010-000"))
                .thenReturn(new BigDecimal("25.00"));

        var response = shippingService.quoteByCep("80010-000");

        assertThat(response.estimatedDays()).isEqualTo(7);
    }

    @Test
    void shouldRejectInvalidUfOnQuote() {
        when(viaCepClient.findByCep("00000000"))
                .thenReturn(new ViaCepResponse("00000000", "Rua", "", "Centro", "Cidade", " ", false));
        when(freightService.calculateFreightByDestinationCep("00000000"))
                .thenReturn(new BigDecimal("30.00"));

        assertThatThrownBy(() -> shippingService.quoteByCep("00000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UF inválida");
    }

    @Test
    void shouldQuoteShippingForSudesteOutsideSaoPauloWithFiveDays() {
        when(viaCepClient.findByCep("20040-020"))
                .thenReturn(new ViaCepResponse("20040-020", "Rua", "", "Centro", "Rio de Janeiro", " rj ", false));
        when(freightService.calculateFreightByDestinationCep("20040-020"))
                .thenReturn(new BigDecimal("20.00"));

        var response = shippingService.quoteByCep("20040-020");

        assertThat(response.estimatedDays()).isEqualTo(5);
        assertThat(response.shippingPrice()).isEqualByComparingTo("20.00");
        assertThat(response.cep()).isEqualTo("20040020");
    }

    @Test
    void shouldQuoteShippingForCentroOesteWithSevenDays() {
        when(viaCepClient.findByCep("74000-000"))
                .thenReturn(new ViaCepResponse("74000-000", "Rua", "", "Centro", "Goiânia", "GO", false));
        when(freightService.calculateFreightByDestinationCep("74000-000"))
                .thenReturn(new BigDecimal("30.00"));

        var response = shippingService.quoteByCep("74000-000");

        assertThat(response.estimatedDays()).isEqualTo(7);
        assertThat(response.city()).isEqualTo("Goiânia");
    }

    @Test
    void shouldQuoteShippingForOtherRegionsWithTenDays() {
        when(viaCepClient.findByCep("40020-000"))
                .thenReturn(new ViaCepResponse("40020-000", "Rua", "", "Centro", "Salvador", "BA", false));
        when(freightService.calculateFreightByDestinationCep("40020-000"))
                .thenReturn(new BigDecimal("35.00"));

        var response = shippingService.quoteByCep("40020-000");

        assertThat(response.estimatedDays()).isEqualTo(10);
        assertThat(response.state()).isEqualTo("BA");
    }

    @Test
    void shouldNormalizeNullCepFromViaCepAddressResponse() {
        when(viaCepClient.findByCep("00000000"))
                .thenReturn(new ViaCepResponse(null, "Rua", "", "Centro", "Cidade", "SP", false));

        var response = shippingService.findAddressByCep("00000000");

        assertThat(response.cep()).isNull();
        assertThat(response.state()).isEqualTo("SP");
    }

    @Test
    void shouldRejectNullUfOnQuote() {
        when(viaCepClient.findByCep("00000000"))
                .thenReturn(new ViaCepResponse("00000000", "Rua", "", "Centro", "Cidade", null, false));
        when(freightService.calculateFreightByDestinationCep("00000000"))
                .thenReturn(new BigDecimal("30.00"));

        assertThatThrownBy(() -> shippingService.quoteByCep("00000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UF inválida");
    }

}
