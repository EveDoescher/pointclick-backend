package com.pim.ecommerce.config;

import com.pim.ecommerce.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.time.Instant;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationProvider authenticationProvider;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

                            writeSecurityError(
                                    response,
                                    HttpStatus.UNAUTHORIZED,
                                    "Não autenticado",
                                    "É necessário estar autenticado para acessar este recurso.",
                                    request.getRequestURI()
                            );
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

                            writeSecurityError(
                                    response,
                                    HttpStatus.FORBIDDEN,
                                    "Acesso negado",
                                    "Você não tem permissão para acessar este recurso.",
                                    request.getRequestURI()
                            );
                        })
                )
                .authorizeHttpRequests(auth -> auth

                        // Swagger / OpenAPI
                        .requestMatchers(
                                "/docs",
                                "/docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // Health
                        .requestMatchers(HttpMethod.GET, "/").permitAll()
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()

                        // Autenticação
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/auth/me").hasAnyRole("CUSTOMER", "ADMIN")

                        // Cadastro público
                        .requestMatchers(HttpMethod.POST, "/users").permitAll()

                        // Arquivos públicos
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()

                        // Upload de produto
                        .requestMatchers(HttpMethod.POST, "/uploads/products").hasRole("ADMIN")

                        // CEP e frete
                        .requestMatchers(HttpMethod.GET, "/shipping/cep/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/shipping/quote").permitAll()

                        // Produtos - rotas admin precisam vir antes das públicas
                        .requestMatchers(HttpMethod.GET, "/products/admin").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/products/admin/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/products/admin/categories").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/products").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/products/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/products/*/activate").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/products/*/deactivate").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/products/*").hasRole("ADMIN")

                        // Produtos - catálogo público
                        .requestMatchers(HttpMethod.GET, "/products").permitAll()
                        .requestMatchers(HttpMethod.GET, "/products/categories").permitAll()
                        .requestMatchers(HttpMethod.GET, "/products/brands").permitAll()
                        .requestMatchers(HttpMethod.GET, "/products/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/products/*/related").permitAll()

                        // Avaliações em produtos
                        .requestMatchers(HttpMethod.GET, "/products/*/reviews").permitAll()
                        .requestMatchers(HttpMethod.GET, "/products/*/reviews/summary").permitAll()
                        .requestMatchers(HttpMethod.POST, "/orders/*/products/*/reviews").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/reviews/my").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/reviews/*").hasAnyRole("CUSTOMER", "ADMIN")

                        // Usuários - admin
                        .requestMatchers(HttpMethod.GET, "/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users/admin/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/users/*/activate").hasRole("ADMIN")

                        // Usuário logado
                        .requestMatchers(HttpMethod.GET, "/users/me").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/users/me/profile").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/users/me/address").hasAnyRole("CUSTOMER", "ADMIN")

                        // Usuários individuais
                        .requestMatchers(HttpMethod.GET, "/users/*").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/users/*").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/users/*").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users/*/orders").hasAnyRole("CUSTOMER", "ADMIN")

                        // Pedidos - listagens específicas
                        .requestMatchers(HttpMethod.GET, "/orders/current").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/orders/my").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/orders/summary").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/orders/admin/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/orders").hasRole("ADMIN")

                        // Pedidos - carrinho e cliente
                        .requestMatchers(HttpMethod.POST, "/orders").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/orders/*").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/orders/*/items").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/orders/*/items/*").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/orders/*/items/*").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/orders/*/shipping/quote").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/orders/*/coupon").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/orders/*/coupon").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/orders/*/close").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/orders/*/cancel").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/orders/*/confirm-delivery").hasAnyRole("CUSTOMER", "ADMIN")

                        // Pedidos - admin
                        .requestMatchers(HttpMethod.POST, "/orders/*/ship").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/orders/*/deliver").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/orders/*/finish").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/orders/*/reopen-expired").hasRole("ADMIN")

                        // Confirmação pública de pagamento simulado protegida por token público
                        .requestMatchers(HttpMethod.GET, "/payments/*/pix-confirmation").permitAll()
                        .requestMatchers(HttpMethod.POST, "/payments/*/confirm-pix").permitAll()
                        .requestMatchers(HttpMethod.GET, "/payments/*/bank-slip-confirmation").permitAll()
                        .requestMatchers(HttpMethod.POST, "/payments/*/confirm-bank-slip").permitAll()

                        // Pagamentos
                        .requestMatchers(HttpMethod.POST, "/payments/orders/*").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/payments/orders/*").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/payments/*/cancel").hasAnyRole("CUSTOMER", "ADMIN")

                        // Notificações
                        .requestMatchers("/notifications/**").hasAnyRole("CUSTOMER", "ADMIN")

                        // Favoritos
                        .requestMatchers("/favorites/**").hasAnyRole("CUSTOMER", "ADMIN")

                        // Cupons - gerenciamento administrativo
                        .requestMatchers("/coupons/**").hasRole("ADMIN")

                        // Admin geral
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // Qualquer outra rota exige autenticação
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private void writeSecurityError(
            jakarta.servlet.http.HttpServletResponse response,
            HttpStatus status,
            String title,
            String detail,
            String instance
    ) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String json = """
            {
              "timestamp": "%s",
              "status": %d,
              "title": "%s",
              "detail": "%s",
              "instance": "%s"
            }
            """.formatted(
                escapeJson(Instant.now().toString()),
                status.value(),
                escapeJson(title),
                escapeJson(detail),
                escapeJson(instance)
        );

        response.getWriter().write(json);
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

}