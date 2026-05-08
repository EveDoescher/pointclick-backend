package com.pim.ecommerce.controller;

import com.pim.ecommerce.dto.response.AdminDashboardResponse;
import com.pim.ecommerce.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(
        name = "12 - Admin",
        description = """
                Endpoints gerais do painel administrativo.

                Esta seção concentra indicadores e recursos globais do admin.
                """
)
public class AdminController {

    private final AdminDashboardService adminDashboardService;

    @Operation(
            summary = "01 - Buscar dashboard administrativo",
            description = """
                    Retorna indicadores gerais do e-commerce:

                    - total de pedidos;
                    - pedidos por status;
                    - receita simulada;
                    - produtos ativos/inativos;
                    - produtos sem estoque;
                    - produtos com baixo estoque;
                    - usuários ativos/inativos;
                    - clientes e administradores.
                    """
    )
    @GetMapping("/dashboard")
    public AdminDashboardResponse getDashboard() {
        return adminDashboardService.getDashboard();
    }
}