package com.pim.ecommerce.controller;

import com.pim.ecommerce.dto.request.LoginRequest;
import com.pim.ecommerce.dto.request.RefreshTokenRequest;
import com.pim.ecommerce.dto.response.AuthenticatedUserResponse;
import com.pim.ecommerce.dto.response.ErrorResponse;
import com.pim.ecommerce.dto.response.LoginResponse;
import com.pim.ecommerce.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(
        name = "02 - Autenticação",
        description = """
                Endpoints responsáveis pela autenticação e manutenção da sessão.

                Fluxo recomendado:
                1. POST /auth/login para obter access token e refresh token;
                2. Usar access token no header Authorization: Bearer {token};
                3. POST /auth/refresh para renovar a sessão quando o access token expirar;
                4. POST /auth/logout para invalidar o refresh token.
                """
)
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "01 - Realizar login",
            description = """
                    Autentica um usuário a partir de e-mail e senha.

                    Regras:
                    - O usuário precisa existir;
                    - A senha precisa corresponder ao hash salvo;
                    - O usuário precisa estar ativo;
                    - Em caso de falha, a mensagem deve ser genérica.

                    Retorna:
                    - access token JWT;
                    - refresh token;
                    - tempo de expiração do access token;
                    - dados básicos do usuário autenticado.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Login realizado com sucesso",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LoginResponse.class),
                            examples = @ExampleObject(
                                    name = "Login bem-sucedido",
                                    value = """
                                            {
                                              "token": "eyJhbGciOiJIUzI1NiJ9...",
                                              "type": "Bearer",
                                              "refreshToken": "4e5d2c74-6f42-48d3-b3d7-8a1c5a4a9f90",
                                              "expiresIn": 3600000,
                                              "user": {
                                                "id": 2,
                                                "fullName": "Ana Souza",
                                                "email": "ana@email.com",
                                                "role": "CUSTOMER"
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Credenciais inválidas ou dados inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/login")
    public LoginResponse login(
            @org.springframework.web.bind.annotation.RequestBody @Valid LoginRequest request
    ) {
        return authService.login(request);
    }

    @Operation(
            summary = "02 - Renovar sessão",
            description = """
                    Renova a sessão usando um refresh token válido.

                    Regras:
                    - O refresh token precisa existir;
                    - O refresh token não pode estar expirado;
                    - O refresh token não pode estar revogado;
                    - Ao renovar, o refresh token antigo é revogado e um novo é emitido.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Sessão renovada com sucesso",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Refresh token inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/refresh")
    public LoginResponse refresh(
            @org.springframework.web.bind.annotation.RequestBody @Valid RefreshTokenRequest request
    ) {
        return authService.refresh(request);
    }

    @Operation(
            summary = "03 - Logout",
            description = """
                    Invalida o refresh token informado.

                    O access token atual continua válido até expirar, pois a API usa JWT stateless.
                    O objetivo deste endpoint é impedir novas renovações de sessão.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logout realizado com sucesso"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Refresh token inválido",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @org.springframework.web.bind.annotation.RequestBody @Valid RefreshTokenRequest request
    ) {
        authService.logout(request);
    }

    @Operation(
            summary = "04 - Buscar usuário autenticado",
            description = """
                    Retorna dados mínimos do usuário autenticado a partir do token JWT.

                    Este endpoint pode ser usado pelo frontend para validar sessão
                    sem carregar todos os dados cadastrais do usuário.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Usuário autenticado retornado com sucesso",
                    content = @Content(schema = @Schema(implementation = AuthenticatedUserResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Usuário não autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/me")
    public AuthenticatedUserResponse me(Authentication authentication) {
        return authService.me(authentication.getName());
    }
}