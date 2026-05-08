package com.pim.ecommerce.controller;

import com.pim.ecommerce.dto.response.ErrorResponse;
import com.pim.ecommerce.dto.response.ReviewResponse;
import com.pim.ecommerce.dto.response.ReviewSummaryResponse;
import com.pim.ecommerce.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(
        name = "11 - Avaliações",
        description = """
                Endpoints responsáveis pelas avaliações de produtos.

                Regra principal:
                O usuário só pode avaliar um produto se comprou esse produto
                e o pedido está FINISHED.
                """
)
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(
            summary = "01 - Avaliar produto de um pedido",
            description = """
                    Cria uma avaliação para um produto comprado em um pedido.

                    Regras:
                    - usuário precisa estar autenticado;
                    - pedido precisa pertencer ao usuário;
                    - pedido precisa estar FINISHED;
                    - produto precisa pertencer ao pedido;
                    - usuário só pode avaliar uma vez o mesmo produto dentro do mesmo pedido;
                    - permite anexar até 5 imagens.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Avaliação criada com sucesso",
                    content = @Content(schema = @Schema(implementation = ReviewResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Usuário não pode avaliar este produto ou dados inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping(
            value = "/orders/{orderId}/products/{productId}/reviews",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(
            @PathVariable Long orderId,
            @PathVariable Long productId,
            @RequestParam("rating") Integer rating,
            @RequestParam(value = "comment", required = false) String comment,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        return reviewService.create(orderId, productId, rating, comment, images);
    }

    @Operation(
            summary = "02 - Listar avaliações de produto",
            description = """
                    Lista avaliações ativas de um produto.

                    Filtros opcionais:
                    - rating: nota exata de 1 a 5;
                    - withComment: true para retornar apenas avaliações com comentário;
                    - withMedia: true para retornar apenas avaliações com imagens.
                    """
    )
    @GetMapping("/products/{productId}/reviews")
    public List<ReviewResponse> findByProductId(
            @PathVariable Long productId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Boolean withComment,
            @RequestParam(required = false) Boolean withMedia
    ) {
        return reviewService.findByProductId(productId, rating, withComment, withMedia);
    }

    @Operation(
            summary = "02.1 - Resumo das avaliações do produto",
            description = "Retorna média, total e contadores por nota, comentário e mídia."
    )
    @GetMapping("/products/{productId}/reviews/summary")
    public ReviewSummaryResponse getSummaryByProductId(
            @PathVariable Long productId
    ) {
        return reviewService.getSummaryByProductId(productId);
    }

    @Operation(
            summary = "03 - Listar minhas avaliações",
            description = "Lista avaliações feitas pelo usuário autenticado."
    )
    @GetMapping("/reviews/my")
    public List<ReviewResponse> findMyReviews() {
        return reviewService.findMyReviews();
    }

    @Operation(
            summary = "04 - Remover avaliação",
            description = """
                    Remove logicamente uma avaliação.

                    Regras:
                    - usuário pode remover a própria avaliação;
                    - ADMIN pode remover qualquer avaliação.
                    """
    )
    @DeleteMapping("/reviews/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long reviewId
    ) {
        reviewService.delete(reviewId);
    }
}