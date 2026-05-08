package com.pim.ecommerce.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodeGeneratorServiceTest {

    private final CodeGeneratorService codeGeneratorService = new CodeGeneratorService();

    @Test
    void shouldGenerateQrCodeBase64() {
        String result = codeGeneratorService.generateQrCodeBase64("https://pointclick.test/payments/1");

        assertThat(result).startsWith("data:image/png;base64,");
        assertThat(result.length()).isGreaterThan(100);
    }

    @Test
    void shouldGenerateCode128BarcodeBase64() {
        String result = codeGeneratorService.generateCode128BarCodeBase64("34195000000000000000000000000000000000000001");

        assertThat(result).startsWith("data:image/png;base64,");
        assertThat(result.length()).isGreaterThan(100);
    }

    @Test
    void shouldRejectBlankContentForQrCode() {
        assertThatThrownBy(() -> codeGeneratorService.generateQrCodeBase64("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Conteúdo para geração do código é obrigatório");
    }
}
