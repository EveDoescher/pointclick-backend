package com.pim.ecommerce.controller;

import com.pim.ecommerce.domain.entity.enums.UserRole;
import com.pim.ecommerce.dto.request.CreateUserRequest;
import com.pim.ecommerce.dto.request.UpdateAddressRequest;
import com.pim.ecommerce.dto.request.UpdateMyProfileRequest;
import com.pim.ecommerce.dto.request.UpdateUserRequest;
import com.pim.ecommerce.dto.response.ErrorResponse;
import com.pim.ecommerce.dto.response.OrderSummaryResponse;
import com.pim.ecommerce.dto.response.UserResponse;
import com.pim.ecommerce.service.OrderService;
import com.pim.ecommerce.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(
        name = "01 - Usuários",
        description = """
                Endpoints responsáveis pelo gerenciamento de usuários.

                Fluxo recomendado:
                1. Criar usuário com cadastro simplificado;
                2. Realizar login;
                3. Buscar usuário logado;
                4. Completar perfil e endereço;
                5. Consultar histórico de pedidos;
                6. Gerenciar usuários no painel admin.
                """
)
public class UserController {

    private final UserService userService;
    private final OrderService orderService;

    @Operation(
            summary = "01 - Cadastrar usuário",
            description = """
                    Cadastra um novo usuário ativo com perfil CUSTOMER.

                    O cadastro é simplificado:
                    - nome completo;
                    - e-mail;
                    - senha.

                    CPF, telefone e endereço podem ser preenchidos depois em Minha Conta
                    ou durante o checkout.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Usuário cadastrado com sucesso",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "id": 2,
                                              "fullName": "Ana Souza",
                                              "email": "ana@email.com",
                                              "cpf": null,
                                              "phone": null,
                                              "role": "CUSTOMER",
                                              "active": true,
                                              "address": null,
                                              "profileCompleteForCheckout": false,
                                              "createdAt": "2026-04-27T20:00:00",
                                              "updatedAt": "2026-04-27T20:00:00"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados inválidos ou e-mail duplicado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(
            @org.springframework.web.bind.annotation.RequestBody @Valid CreateUserRequest request
    ) {
        return userService.create(request);
    }

    @Operation(
            summary = "02 - Listar usuários para admin",
            description = """
                    Lista usuários para o painel administrativo.

                    Filtros opcionais:
                    - active: true ou false;
                    - role: CUSTOMER ou ADMIN.

                    A senha nunca é retornada.
                    """
    )
    @GetMapping
    public List<UserResponse> findAll(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) UserRole role
    ) {
        return userService.findAllForAdmin(active, role);
    }

    @Operation(
            summary = "03 - Buscar usuário logado",
            description = """
                    Retorna os dados completos do usuário autenticado.

                    Usado pelo frontend para:
                    - manter sessão após recarregar;
                    - montar header/navbar;
                    - verificar perfil;
                    - verificar se possui endereço antes de fechar pedido.
                    """
    )
    @GetMapping("/me")
    public UserResponse findMe() {
        return userService.findMe();
    }

    @Operation(
            summary = "04 - Atualizar perfil do usuário logado",
            description = """
                    Atualiza parcialmente o perfil do usuário autenticado.

                    Campos aceitos:
                    - fullName;
                    - cpf;
                    - phone.

                    Não altera e-mail, senha ou endereço.
                    O endereço deve ser atualizado em PUT /users/me/address.
                    """
    )
    @PatchMapping("/me/profile")
    public UserResponse updateMyProfile(
            @org.springframework.web.bind.annotation.RequestBody @Valid UpdateMyProfileRequest request
    ) {
        return userService.updateMyProfile(request);
    }

    @Operation(
            summary = "04.1 - Atualizar foto do usuário logado",
            description = """
                Atualiza a foto/avatar do usuário autenticado.

                Regras:
                - aceita JPG, PNG, WEBP ou GIF;
                - tamanho máximo de 5MB;
                - retorna os dados atualizados do usuário.
                """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Foto atualizada com sucesso",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Arquivo inválido",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse updateMyAvatar(
            @RequestPart("file") MultipartFile file
    ) {
        return userService.updateMyAvatar(file);
    }

    @Operation(
            summary = "05 - Cadastrar ou atualizar endereço do usuário logado",
            description = """
                    Cadastra ou atualiza o endereço principal do usuário autenticado.

                    Usado:
                    - na tela Minha Conta;
                    - no checkout;
                    - quando o usuário tenta fechar carrinho sem endereço.
                    """
    )
    @PutMapping("/me/address")
    public UserResponse updateMyAddress(
            @org.springframework.web.bind.annotation.RequestBody @Valid UpdateAddressRequest request
    ) {
        return userService.updateMyAddress(request);
    }

    @Operation(
            summary = "06 - Buscar usuário ativo por ID",
            description = """
                    Busca um usuário ativo pelo ID.

                    Regras:
                    - CUSTOMER só pode buscar a si mesmo;
                    - ADMIN pode buscar qualquer usuário ativo.
                    """
    )
    @GetMapping("/{id}")
    public UserResponse findById(
            @Parameter(description = "ID do usuário", example = "2", required = true)
            @PathVariable Long id
    ) {
        return userService.findById(id);
    }

    @Operation(
            summary = "07 - Buscar usuário por ID para admin",
            description = """
                    Busca um usuário por ID no painel administrativo.

                    Diferente da busca comum, pode retornar usuário inativo.
                    """
    )
    @GetMapping("/admin/{id}")
    public UserResponse findByIdForAdmin(
            @Parameter(description = "ID do usuário", example = "2", required = true)
            @PathVariable Long id
    ) {
        return userService.findByIdForAdmin(id);
    }

    @Operation(
            summary = "08 - Atualizar usuário",
            description = """
                    Atualiza os dados completos de um usuário ativo.

                    Regras:
                    - CUSTOMER só pode atualizar a si mesmo;
                    - ADMIN pode atualizar qualquer usuário ativo;
                    - Não altera senha;
                    - Endereço é atualizado junto.
                    """
    )
    @PutMapping("/{id}")
    public UserResponse update(
            @Parameter(description = "ID do usuário que será atualizado", example = "2", required = true)
            @PathVariable Long id,

            @org.springframework.web.bind.annotation.RequestBody @Valid UpdateUserRequest request
    ) {
        return userService.update(id, request);
    }

    @Operation(
            summary = "09 - Listar histórico de pedidos do usuário",
            description = """
                    Retorna o histórico resumido de pedidos de um usuário.

                    Regras:
                    - CUSTOMER só pode ver seus próprios pedidos;
                    - ADMIN pode ver pedidos de qualquer usuário.
                    """
    )
    @GetMapping("/{userId}/orders")
    public List<OrderSummaryResponse> findOrdersByUserId(
            @Parameter(description = "ID do usuário dono dos pedidos", example = "2", required = true)
            @PathVariable Long userId
    ) {
        return orderService.findByUserId(userId);
    }

    @Operation(
            summary = "10 - Excluir usuário logicamente",
            description = """
                    Desativa um usuário.

                    Regras:
                    - O usuário não é removido fisicamente do banco;
                    - active passa para false;
                    - refresh tokens do usuário são revogados.
                    """
    )
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "ID do usuário que será desativado", example = "2", required = true)
            @PathVariable Long id
    ) {
        userService.delete(id);
    }

    @Operation(
            summary = "11 - Reativar usuário",
            description = """
                    Reativa um usuário inativo.

                    Uso administrativo.
                    """
    )
    @PatchMapping("/{id}/activate")
    public UserResponse reactivate(
            @Parameter(description = "ID do usuário que será reativado", example = "2", required = true)
            @PathVariable Long id
    ) {
        return userService.reactivate(id);
    }
}