package com.pim.ecommerce.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;

@Service
public class CodeGeneratorService {

    public String generateQrCodeBase64(String content) {
        validateContent(content);

        try {
            BitMatrix matrix = new MultiFormatWriter().encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    300,
                    300,
                    Map.of(EncodeHintType.MARGIN, 1)
            );

            return toPngBase64(matrix);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Não foi possível gerar o QR Code");
        }
    }

    public String generateCode128BarCodeBase64(String content) {
        validateContent(content);

        try {
            BitMatrix matrix = new MultiFormatWriter().encode(
                    content,
                    BarcodeFormat.CODE_128,
                    600,
                    160,
                    Map.of(EncodeHintType.MARGIN, 10)
            );

            return toPngBase64(matrix);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Não foi possível gerar o código de barras");
        }
    }

    private String toPngBase64(BitMatrix matrix) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", outputStream);

        String base64 = Base64.getEncoder().encodeToString(outputStream.toByteArray());

        return "data:image/png;base64," + base64;
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Conteúdo para geração do código é obrigatório");
        }
    }
}