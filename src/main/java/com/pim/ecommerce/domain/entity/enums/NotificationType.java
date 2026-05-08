package com.pim.ecommerce.domain.entity.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tipo da notificação enviada ao usuário")
public enum NotificationType {

    @Schema(description = "Pagamento aprovado")
    PAYMENT_APPROVED,

    @Schema(description = "Pedido enviado e a caminho")
    ORDER_SHIPPED,

    @Schema(description = "Pedido marcado como entregue pela loja")
    ORDER_DELIVERED,

    @Schema(description = "Pedido finalizado após confirmação do cliente")
    ORDER_FINISHED,

    @Schema(description = "Pedido cancelado")
    ORDER_CANCELLED,

    @Schema(description = "Produto favorito entrou em promoção")
    FAVORITE_PRODUCT_PROMOTION,

    @Schema(description = "Produto favorito voltou ao estoque")
    FAVORITE_PRODUCT_BACK_IN_STOCK
}