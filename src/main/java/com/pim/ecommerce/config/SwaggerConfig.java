package com.pim.ecommerce.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "PointClick Backend API",
                version = "2.0.0",
                description = """
                        API REST para um MVP de e-commerce de produtos eletrônicos.

                        Fluxo principal recomendado para consumo da API:

                        1. Criar usuário com cadastro simplificado;
                        2. Realizar login;
                        3. Consultar vitrine de produtos;
                        4. Criar pedido/carrinho;
                        5. Adicionar itens ao pedido;
                        6. Simular frete, se necessário;
                        7. Aplicar cupom, se houver;
                        8. Fechar pedido;
                        9. Criar pagamento simulado;
                        10. Confirmar pagamento, se for PIX ou boleto;
                        11. Admin marca pedido como enviado;
                        12. Admin marca pedido como entregue;
                        13. Cliente confirma recebimento;
                        14. Pedido vira finalizado.

                        Fluxo de status do pedido:

                        PENDING:
                        - carrinho aberto;
                        - itens ainda podem ser alterados;
                        - cupom pode ser aplicado ou removido.

                        CLOSED:
                        - pedido fechado;
                        - endereço, frete e preços congelados;
                        - estoque reservado;
                        - aguardando pagamento.

                        PAID:
                        - pagamento aprovado;
                        - estoque baixado definitivamente;
                        - aguardando envio.

                        SHIPPED:
                        - pedido enviado;
                        - está a caminho do cliente.

                        DELIVERED:
                        - pedido marcado como entregue pela loja/admin;
                        - cliente deve confirmar recebimento.

                        FINISHED:
                        - cliente confirmou recebimento;
                        - compra finalizada.

                        CANCELLED:
                        - pedido cancelado.

                        Observação:
                        Esta API simula pagamentos e não realiza transações financeiras reais.
                        PIX e boleto geram URLs de confirmação para fins acadêmicos/demonstração.
                        """,
                license = @License(name = "Academic Project")
        ),
        security = {
                @SecurityRequirement(name = "bearerAuth")
        },
        tags = {
                @Tag(
                        name = "01 - Usuários",
                        description = """
                                Cadastro e gerenciamento de usuários.

                                Inclui:
                                - cadastro simplificado;
                                - usuário logado;
                                - atualização de perfil;
                                - atualização de endereço;
                                - histórico de pedidos;
                                - gerenciamento administrativo de usuários.
                                """
                ),
                @Tag(
                        name = "02 - Autenticação",
                        description = """
                                Login e manutenção de sessão.

                                Inclui:
                                - login;
                                - refresh token;
                                - logout;
                                - consulta do usuário autenticado.
                                """
                ),
                @Tag(
                        name = "03 - Produtos",
                        description = """
                                Catálogo e administração de produtos.

                                Inclui:
                                - vitrine pública;
                                - filtros;
                                - produtos relacionados;
                                - listagem administrativa;
                                - ativação/desativação;
                                - controle de estoque.
                                """
                ),
                @Tag(
                        name = "04 - Pedidos",
                        description = """
                                Carrinho, checkout, entrega e confirmação de recebimento.

                                Inclui:
                                - criação de pedido;
                                - itens do carrinho;
                                - frete;
                                - cupom;
                                - fechamento;
                                - envio;
                                - entrega;
                                - confirmação pelo cliente.
                                """
                ),
                @Tag(
                        name = "05 - Pagamentos",
                        description = """
                                Pagamentos simulados.

                                Inclui:
                                - cartão de crédito;
                                - cartão de débito;
                                - PIX;
                                - boleto;
                                - confirmação pública por token.
                                """
                ),
                @Tag(
                        name = "06 - Frete e CEP",
                        description = """
                                Consulta de CEP e simulação de frete.

                                Usado no cadastro, checkout, carrinho e Minha Conta.
                                """
                ),
                @Tag(
                        name = "07 - Uploads",
                        description = """
                                Upload de arquivos.

                                Usado principalmente para imagem de produto.
                                """
                ),
                @Tag(
                        name = "08 - Notificações",
                        description = """
                                Central de notificações do usuário.

                                Inclui notificações de pedido, pagamento, promoção e estoque.
                                """
                ),
                @Tag(
                        name = "09 - Favoritos",
                        description = """
                                Lista de desejos/favoritos do usuário.

                                Base para notificações de promoção e volta ao estoque.
                                """
                ),
                @Tag(
                        name = "10 - Cupons",
                        description = """
                                Gerenciamento administrativo de cupons.

                                A aplicação ou remoção no carrinho acontece nos endpoints de pedido.
                                """
                ),
                @Tag(
                        name = "11 - Avaliações",
                        description = """
                                Avaliações de produtos.

                                Usuário só pode avaliar produto comprado e com pedido finalizado.
                                """
                ),
                @Tag(
                        name = "12 - Admin",
                        description = """
                                Recursos gerais do painel administrativo.

                                Inclui dashboard e indicadores do e-commerce.
                                """
                )
        }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Autenticação via JWT. Use o access token retornado no login."
)
public class SwaggerConfig {

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .servers(List.of(
                        new Server()
                                .url(appBaseUrl)
                                .description("Servidor atual da aplicação")
                ));
    }
}