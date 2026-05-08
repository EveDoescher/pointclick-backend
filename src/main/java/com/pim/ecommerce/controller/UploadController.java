package com.pim.ecommerce.controller;

import com.pim.ecommerce.dto.response.ErrorResponse;
import com.pim.ecommerce.dto.response.UploadResponse;
import com.pim.ecommerce.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/uploads")
@RequiredArgsConstructor
@Tag(
        name = "07 - Uploads",
        description = """
                Endpoints responsáveis pelo upload de arquivos.

                Fluxo recomendado para imagem de produto:
                1. Admin seleciona uma imagem no frontend;
                2. Frontend envia POST /uploads/products como multipart/form-data;
                3. Backend salva o arquivo;
                4. Backend retorna a URL pública;
                5. Frontend usa a URL retornada no imageUrl do produto.
                """
)
public class UploadController {

    private final FileStorageService fileStorageService;

    @Operation(
            summary = "01 - Upload de imagem de produto",
            description = """
                    Realiza upload de uma imagem para ser usada em produtos.

                    Regras:
                    - apenas ADMIN pode enviar imagem;
                    - campo multipart deve se chamar file;
                    - formatos aceitos: JPG, JPEG, PNG, WEBP e GIF;
                    - tamanho máximo: 5MB;
                    - retorna URL pública para salvar no imageUrl do produto.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Upload realizado com sucesso",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UploadResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Arquivo inválido",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Apenas ADMIN pode enviar imagens",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping(
            value = "/products",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public UploadResponse uploadProductImage(
            @RequestPart("file") MultipartFile file
    ) {
        String url = fileStorageService.storeProductImage(file);

        return new UploadResponse(url);
    }
}