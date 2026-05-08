package com.pim.ecommerce.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resposta do upload de arquivo")
public record UploadResponse(

        @Schema(
                description = "URL pública do arquivo enviado",
                example = "/uploads/products/3f8d3e0e-9f4e-45d7-b3dd-notebook.png"
        )
        String url
) {
}