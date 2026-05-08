package com.pim.ecommerce.controller;

import com.pim.ecommerce.dto.response.ErrorResponse;
import com.pim.ecommerce.dto.response.NotificationResponse;
import com.pim.ecommerce.service.NotificationService;
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
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(
        name = "08 - Notificações",
        description = """
                Endpoints responsáveis pelas notificações do usuário autenticado.

                Notificações possíveis:
                - pagamento aprovado;
                - pedido enviado;
                - pedido entregue;
                - compra finalizada;
                - pedido cancelado;
                - produto favorito em promoção;
                - produto favorito voltou ao estoque.
                """
)
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(
            summary = "01 - Listar minhas notificações",
            description = """
                    Lista notificações do usuário autenticado.

                    Filtro opcional:
                    - unreadOnly=true retorna apenas notificações não lidas;
                    - unreadOnly=false ou ausente retorna todas.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Notificações retornadas com sucesso",
                    content = @Content(schema = @Schema(implementation = NotificationResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Usuário não autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping
    public List<NotificationResponse> findMyNotifications(
            @RequestParam(required = false, defaultValue = "false") Boolean unreadOnly
    ) {
        return notificationService.findMyNotifications(unreadOnly);
    }

    @Operation(
            summary = "02 - Contar notificações não lidas",
            description = """
                    Retorna a quantidade de notificações não lidas do usuário autenticado.

                    Usado pelo frontend para exibir a bolinha no sininho de notificações.
                    """
    )
    @GetMapping("/unread-count")
    public Map<String, Long> countUnreadNotifications() {
        return Map.of("count", notificationService.countMyUnreadNotifications());
    }

    @Operation(
            summary = "03 - Marcar notificação como lida",
            description = "Marca uma notificação específica como lida."
    )
    @PatchMapping("/{notificationId}/read")
    public NotificationResponse markAsRead(
            @PathVariable Long notificationId
    ) {
        return notificationService.markAsRead(notificationId);
    }

    @Operation(
            summary = "04 - Marcar todas as notificações como lidas",
            description = "Marca todas as notificações do usuário autenticado como lidas."
    )
    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllAsRead() {
        notificationService.markAllAsRead();
    }

    @Operation(
            summary = "05 - Excluir notificação",
            description = "Remove uma notificação do usuário autenticado."
    )
    @DeleteMapping("/{notificationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long notificationId
    ) {
        notificationService.delete(notificationId);
    }
}