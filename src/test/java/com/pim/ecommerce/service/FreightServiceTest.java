package com.pim.ecommerce.service;

import com.pim.ecommerce.integration.viacep.ViaCepClient;
import com.pim.ecommerce.integration.viacep.ViaCepResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FreightServiceTest {

    @Mock
    private ViaCepClient viaCepClient;

    @InjectMocks
    private FreightService freightService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(freightService, "shippingOriginCep", "13480370");
    }

    @Test
    void shouldReturnFifteenWhenOriginAndDestinationAreSameState() {
        when(viaCepClient.getAddressByCep("13480370")).thenReturn(address("SP"));
        when(viaCepClient.getAddressByCep("01001000")).thenReturn(address("SP"));

        BigDecimal freight = freightService.calculateFreightByDestinationCep("01001000");

        assertThat(freight).isEqualByComparingTo("15.00");
    }

    @Test
    void shouldReturnTwentyForSudesteDifferentState() {
        when(viaCepClient.getAddressByCep("13480370")).thenReturn(address("SP"));
        when(viaCepClient.getAddressByCep("20040002")).thenReturn(address("RJ"));

        BigDecimal freight = freightService.calculateFreightByDestinationCep("20040002");

        assertThat(freight).isEqualByComparingTo("20.00");
    }

    @Test
    void shouldReturnTwentyFiveForSulOrCentroOeste() {
        when(viaCepClient.getAddressByCep("13480370")).thenReturn(address("SP"));
        when(viaCepClient.getAddressByCep("80010000")).thenReturn(address("PR"));

        BigDecimal freight = freightService.calculateFreightByDestinationCep("80010000");

        assertThat(freight).isEqualByComparingTo("25.00");
    }

    @Test
    void shouldReturnThirtyFiveForNorteOrNordeste() {
        when(viaCepClient.getAddressByCep("13480370")).thenReturn(address("SP"));
        when(viaCepClient.getAddressByCep("40010000")).thenReturn(address("BA"));

        BigDecimal freight = freightService.calculateFreightByDestinationCep("40010000");

        assertThat(freight).isEqualByComparingTo("35.00");
    }

    @Test
    void shouldRejectInvalidUf() {
        when(viaCepClient.getAddressByCep("13480370")).thenReturn(address("SP"));
        when(viaCepClient.getAddressByCep("00000000")).thenReturn(address(" "));

        assertThatThrownBy(() -> freightService.calculateFreightByDestinationCep("00000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UF inválida para cálculo de frete");
    }

    private ViaCepResponse address(String uf) {
        return new ViaCepResponse("00000000", "Rua", "", "Centro", "Cidade", uf, false);
    }
}
