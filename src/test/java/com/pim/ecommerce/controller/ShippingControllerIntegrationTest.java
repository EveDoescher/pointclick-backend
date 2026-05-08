package com.pim.ecommerce.controller;

import com.pim.ecommerce.integration.viacep.ViaCepClient;
import com.pim.ecommerce.integration.viacep.ViaCepResponse;
import com.pim.ecommerce.service.FreightService;
import com.pim.ecommerce.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShippingControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ViaCepClient viaCepClient;

    @MockitoBean
    private FreightService freightService;

    @BeforeEach
    void setUp() {
        reset(viaCepClient, freightService);
    }

    @Test
    void shouldFindAddressByCep() throws Exception {
        when(viaCepClient.findByCep("13480-370"))
                .thenReturn(new ViaCepResponse(
                        "13480-370",
                        "Rua das Flores",
                        "Casa",
                        "Centro",
                        "Limeira",
                        "SP",
                        false
                ));

        mockMvc.perform(get("/shipping/cep/{cep}", "13480-370"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cep").value("13480370"))
                .andExpect(jsonPath("$.street").value("Rua das Flores"))
                .andExpect(jsonPath("$.district").value("Centro"))
                .andExpect(jsonPath("$.city").value("Limeira"))
                .andExpect(jsonPath("$.state").value("SP"));
    }

    @Test
    void shouldQuoteShippingByCep() throws Exception {
        when(viaCepClient.findByCep("13480-370"))
                .thenReturn(new ViaCepResponse(
                        "13480-370",
                        "Rua das Flores",
                        "Casa",
                        "Centro",
                        "Limeira",
                        "SP",
                        false
                ));
        when(freightService.calculateFreightByDestinationCep("13480-370"))
                .thenReturn(new BigDecimal("15.00"));

        mockMvc.perform(get("/shipping/quote")
                        .param("cep", "13480-370"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cep").value("13480370"))
                .andExpect(jsonPath("$.shippingPrice").value(15.00))
                .andExpect(jsonPath("$.estimatedDays").value(3))
                .andExpect(jsonPath("$.street").value("Rua das Flores"))
                .andExpect(jsonPath("$.district").value("Centro"))
                .andExpect(jsonPath("$.city").value("Limeira"))
                .andExpect(jsonPath("$.state").value("SP"));
    }

    @Test
    void shouldReturnBadRequestWhenCepIsInvalid() throws Exception {
        when(viaCepClient.findByCep("123"))
                .thenThrow(new IllegalArgumentException("CEP deve conter 8 dígitos"));

        mockMvc.perform(get("/shipping/cep/{cep}", "123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.detail").value("CEP deve conter 8 dígitos"));
    }
}
