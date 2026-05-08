package com.pim.ecommerce.dto.response;

import com.pim.ecommerce.domain.entity.enums.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Resposta com dados de cupom de desconto")
public record CouponResponse(

        @Schema(description = "ID do cupom", example = "1")
        Long id,

        @Schema(description = "Código do cupom", example = "POINT10")
        String code,

        @Schema(description = "Descrição administrativa do cupom", example = "Cupom de boas-vindas", nullable = true)
        String description,

        @Schema(description = "Tipo de desconto", example = "PERCENTAGE")
        DiscountType discountType,

        @Schema(description = "Valor do desconto", example = "10.00")
        BigDecimal discountValue,

        @Schema(description = "Valor mínimo dos itens do pedido para aplicar o cupom", example = "100.00")
        BigDecimal minimumOrderValue,

        @Schema(description = "Indica se o cupom está ativo", example = "true")
        Boolean active,

        @Schema(description = "Início da validade", example = "2026-05-01T00:00:00", nullable = true)
        LocalDateTime startsAt,

        @Schema(description = "Fim da validade", example = "2026-05-31T23:59:59", nullable = true)
        LocalDateTime endsAt,

        @Schema(description = "Limite total de usos", example = "100", nullable = true)
        Integer usageLimit,

        @Schema(description = "Quantidade de usos já realizados", example = "12")
        Integer usedCount,

        @Schema(description = "Indica se o cupom está válido no momento atual", example = "true")
        Boolean currentlyValid,

        @Schema(description = "Data de criação do cupom", example = "2026-05-01T10:00:00")
        LocalDateTime createdAt,

        @Schema(description = "Data da última atualização do cupom", example = "2026-05-01T10:30:00")
        LocalDateTime updatedAt
) {
}