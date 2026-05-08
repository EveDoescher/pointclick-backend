package com.pim.ecommerce.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Resposta com indicadores gerais do painel administrativo")
public record AdminDashboardResponse(

        @Schema(description = "Total de pedidos cadastrados", example = "40")
        Long totalOrders,

        @Schema(description = "Pedidos pendentes/carrinho aberto", example = "4")
        Long pendingOrders,

        @Schema(description = "Pedidos fechados aguardando pagamento", example = "2")
        Long closedOrders,

        @Schema(description = "Pedidos pagos aguardando envio", example = "8")
        Long paidOrders,

        @Schema(description = "Pedidos enviados e a caminho", example = "3")
        Long shippedOrders,

        @Schema(description = "Pedidos marcados como entregues aguardando confirmação", example = "2")
        Long deliveredOrders,

        @Schema(description = "Pedidos finalizados", example = "20")
        Long finishedOrders,

        @Schema(description = "Pedidos cancelados", example = "1")
        Long cancelledOrders,

        @Schema(description = "Receita total simulada considerando pedidos pagos/em andamento/finalizados", example = "12500.90")
        BigDecimal totalRevenue,

        @Schema(description = "Produtos ativos", example = "22")
        Long activeProducts,

        @Schema(description = "Produtos inativos", example = "3")
        Long inactiveProducts,

        @Schema(description = "Produtos sem estoque disponível", example = "2")
        Long outOfStockProducts,

        @Schema(description = "Produtos com baixo estoque", example = "5")
        Long lowStockProducts,

        @Schema(description = "Usuários ativos", example = "15")
        Long activeUsers,

        @Schema(description = "Usuários inativos", example = "1")
        Long inactiveUsers,

        @Schema(description = "Clientes cadastrados", example = "14")
        Long customerUsers,

        @Schema(description = "Administradores cadastrados", example = "1")
        Long adminUsers
) {
}