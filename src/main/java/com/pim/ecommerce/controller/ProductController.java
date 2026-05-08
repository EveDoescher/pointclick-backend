package com.pim.ecommerce.controller;

import com.pim.ecommerce.dto.request.CreateProductRequest;
import com.pim.ecommerce.dto.request.UpdateProductRequest;
import com.pim.ecommerce.dto.response.ErrorResponse;
import com.pim.ecommerce.dto.response.ProductResponse;
import com.pim.ecommerce.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Tag(
        name = "03 - Produtos",
        description = """
                Endpoints responsáveis pelo catálogo e administração de produtos.

                Regras principais:
                - A listagem pública retorna apenas produtos ativos;
                - A listagem administrativa pode retornar ativos e inativos;
                - Produtos inativos não aparecem na vitrine;
                - A exclusão é lógica;
                - O estoque físico só é reduzido após pagamento aprovado;
                - O estoque reservado é usado por pedidos CLOSED.
                """
)
public class ProductController {

    private final ProductService productService;

    @Operation(
            summary = "01 - Cadastrar produto",
            description = """
                    Cadastra um novo produto no catálogo.

                    A imagem deve ser enviada antes em POST /uploads/products.
                    O retorno do upload deve ser usado no campo imageUrl.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Produto cadastrado com sucesso",
                    content = @Content(schema = @Schema(implementation = ProductResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(
            @org.springframework.web.bind.annotation.RequestBody @Valid CreateProductRequest request
    ) {
        return productService.create(request);
    }

    @Operation(
            summary = "02 - Listar produtos ativos com filtros",
            description = """
                    Lista produtos ativos da vitrine.

                    Filtros:
                    - search;
                    - category;
                    - categoryGroup;
                    - brand;
                    - minPrice;
                    - maxPrice;
                    - available;
                    - sort.

                    Sorts aceitos:
                    - newest;
                    - price_asc;
                    - price_desc;
                    - name_asc.
                    """
    )
    @GetMapping
    public List<ProductResponse> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String categoryGroup,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean available,
            @RequestParam(required = false, defaultValue = "newest") String sort
    ) {
        return productService.findAll(
                search,
                category,
                categoryGroup,
                brand,
                minPrice,
                maxPrice,
                available,
                sort
        );
    }

    @Operation(
            summary = "03 - Listar produtos para admin",
            description = """
                    Lista produtos para painel administrativo.

                    Diferente da listagem pública, pode retornar produtos ativos e inativos.

                    Filtros:
                    - search;
                    - category;
                    - brand;
                    - active;
                    - minPrice;
                    - maxPrice;
                    - available;
                    - sort.
                    """
    )
    @GetMapping("/admin")
    public List<ProductResponse> findAllForAdmin(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String categoryGroup,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean available,
            @RequestParam(required = false, defaultValue = "newest") String sort
    ) {
        return productService.findAllForAdmin(
                search,
                category,
                categoryGroup,
                brand,
                active,
                minPrice,
                maxPrice,
                available,
                sort
        );
    }

    @Operation(
            summary = "04 - Listar categorias ativas",
            description = "Retorna categorias distintas de produtos ativos."
    )
    @GetMapping("/categories")
    public List<String> findCategories() {
        return productService.findCategories();
    }

    @Operation(
            summary = "04.1 - Listar grupos de categoria ativos",
            description = "Retorna grupos comerciais distintos de produtos ativos."
    )
    @GetMapping("/category-groups")
    public List<String> findCategoryGroups() {
        return productService.findCategoryGroups();
    }

    @Operation(
            summary = "05 - Listar todas as categorias para admin",
            description = "Retorna categorias distintas considerando produtos ativos e inativos."
    )
    @GetMapping("/admin/categories")
    public List<String> findAllCategoriesForAdmin() {
        return productService.findAllCategoriesForAdmin();
    }

    @Operation(
            summary = "05.1 - Listar todos os grupos de categoria para admin",
            description = "Retorna grupos comerciais distintos considerando produtos ativos e inativos."
    )
    @GetMapping("/admin/category-groups")
    public List<String> findAllCategoryGroupsForAdmin() {
        return productService.findAllCategoryGroupsForAdmin();
    }

    @Operation(
            summary = "06 - Listar marcas ativas",
            description = "Retorna marcas distintas de produtos ativos."
    )
    @GetMapping("/brands")
    public List<String> findBrands() {
        return productService.findBrands();
    }

    @Operation(
            summary = "07 - Buscar produto ativo por ID",
            description = """
                    Busca um produto ativo pelo ID.

                    Produto inativo é tratado como não encontrado na rota pública.
                    """
    )
    @GetMapping("/{id}")
    public ProductResponse findById(
            @Parameter(description = "ID do produto", example = "1", required = true)
            @PathVariable Long id
    ) {
        return productService.findById(id);
    }

    @Operation(
            summary = "08 - Buscar produto por ID para admin",
            description = """
                    Busca um produto por ID no painel administrativo.

                    Pode retornar produto ativo ou inativo.
                    """
    )
    @GetMapping("/admin/{id}")
    public ProductResponse findByIdForAdmin(
            @Parameter(description = "ID do produto", example = "1", required = true)
            @PathVariable Long id
    ) {
        return productService.findByIdForAdmin(id);
    }

    @Operation(
            summary = "09 - Listar produtos relacionados",
            description = """
                    Retorna até 4 produtos relacionados ao produto informado.

                    Critério inicial:
                    - mesma categoria;
                    - produto ativo;
                    - exclui o próprio produto.
                    """
    )
    @GetMapping("/{id}/related")
    public List<ProductResponse> findRelated(
            @Parameter(description = "ID do produto base", example = "1", required = true)
            @PathVariable Long id
    ) {
        return productService.findRelated(id);
    }

    @Operation(
            summary = "10 - Atualizar produto",
            description = """
                    Atualiza os dados de um produto.

                    Regras:
                    - Pode alterar nome, descrição, categoria, marca, modelo, preço, estoque, imagem e active;
                    - Se o preço for reduzido, usuários que favoritaram o produto recebem notificação;
                    - Se o produto voltar ao estoque, favoritos recebem notificação.
                    """
    )
    @PutMapping("/{id}")
    public ProductResponse update(
            @Parameter(description = "ID do produto que será atualizado", example = "1", required = true)
            @PathVariable Long id,

            @org.springframework.web.bind.annotation.RequestBody @Valid UpdateProductRequest request
    ) {
        return productService.update(id, request);
    }

    @Operation(
            summary = "11 - Desativar produto",
            description = """
                    Desativa um produto logicamente.

                    Produto inativo:
                    - não aparece na vitrine;
                    - continua no histórico de pedidos;
                    - continua visível no admin.
                    """
    )
    @PatchMapping("/{id}/deactivate")
    public ProductResponse deactivate(
            @Parameter(description = "ID do produto que será desativado", example = "1", required = true)
            @PathVariable Long id
    ) {
        return productService.deactivate(id);
    }

    @Operation(
            summary = "12 - Reativar produto",
            description = """
                    Reativa um produto inativo.

                    Se o produto voltar ao estoque, usuários que o favoritaram podem ser notificados.
                    """
    )
    @PatchMapping("/{id}/activate")
    public ProductResponse activate(
            @Parameter(description = "ID do produto que será reativado", example = "1", required = true)
            @PathVariable Long id
    ) {
        return productService.activate(id);
    }

    @Operation(
            summary = "13 - Excluir produto logicamente",
            description = """
                    Compatibilidade com o fluxo antigo.

                    Executa a mesma regra de desativação lógica do produto.
                    """
    )
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "ID do produto que será desativado", example = "1", required = true)
            @PathVariable Long id
    ) {
        productService.delete(id);
    }
}