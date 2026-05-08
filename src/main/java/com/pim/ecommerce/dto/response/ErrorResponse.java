package com.pim.ecommerce.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Resposta padrão de erro da API")
public record ErrorResponse(

        @Schema(
                description = "Data e hora em que o erro ocorreu",
                example = "2026-04-27T20:45:12.123Z"
        )
        Instant timestamp,

        @Schema(
                description = "Status HTTP do erro",
                example = "400"
        )
        Integer status,

        @Schema(
                description = "Título resumido do erro",
                example = "Requisição inválida"
        )
        String title,

        @Schema(
                description = "Mensagem amigável do erro",
                example = "Produto não encontrado"
        )
        String detail,

        @Schema(
                description = "Caminho do endpoint onde o erro ocorreu",
                example = "/products/99"
        )
        String instance
) {
}