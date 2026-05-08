package com.pim.ecommerce.controller;

import com.pim.ecommerce.dto.response.ErrorResponse;
import com.pim.ecommerce.dto.response.FavoriteResponse;
import com.pim.ecommerce.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/favorites")
@RequiredArgsConstructor
@Tag(
        name = "09 - Favoritos",
        description = """
                Endpoints responsáveis pela lista de desejos/favoritos do usuário.

                Usado para:
                - favoritar produto;
                - remover produto dos favoritos;
                - listar favoritos;
                - verificar se um produto já está favoritado;
                - permitir notificações de promoção e volta ao estoque.
                """
)
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(
            summary = "01 - Listar meus favoritos",
            description = "Retorna todos os produtos favoritados pelo usuário autenticado."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Favoritos retornados com sucesso",
                    content = @Content(schema = @Schema(implementation = FavoriteResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Usuário não autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping
    public List<FavoriteResponse> findMyFavorites() {
        return favoriteService.findMyFavorites();
    }

    @Operation(
            summary = "02 - Favoritar produto",
            description = """
                    Adiciona um produto aos favoritos do usuário autenticado.

                    Se o produto já estiver favoritado, retorna o favorito existente.
                    """
    )
    @PostMapping("/products/{productId}")
    public FavoriteResponse addFavorite(
            @PathVariable Long productId
    ) {
        return favoriteService.addFavorite(productId);
    }

    @Operation(
            summary = "03 - Remover produto dos favoritos",
            description = "Remove um produto dos favoritos do usuário autenticado."
    )
    @DeleteMapping("/products/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFavorite(
            @PathVariable Long productId
    ) {
        favoriteService.removeFavorite(productId);
    }

    @Operation(
            summary = "04 - Verificar se produto está favoritado",
            description = """
                    Retorna true ou false indicando se o produto informado está favoritado
                    pelo usuário autenticado.
                    """
    )
    @GetMapping("/products/{productId}/exists")
    public Map<String, Boolean> existsForCurrentUser(
            @PathVariable Long productId
    ) {
        return Map.of("favorited", favoriteService.existsForCurrentUser(productId));
    }
}