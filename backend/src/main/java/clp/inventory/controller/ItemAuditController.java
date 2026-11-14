package clp.inventory.controller;

import clp.inventory.dto.ItemAuditDto;
import clp.inventory.service.AuthorizationService;
import clp.inventory.service.ItemAuditService;
import clp.inventory.service.ItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auditoria de itens", description = "Histórico de validações de itens.")
@RestController
@RequestMapping("/item-audits")
public class ItemAuditController {

  private final ItemAuditService itemAuditService;
  private final ItemService itemService;
  private final AuthorizationService authorizationService;

  public ItemAuditController(
      ItemAuditService itemAuditService,
      ItemService itemService,
      AuthorizationService authorizationService) {
    this.itemAuditService = itemAuditService;
    this.itemService = itemService;
    this.authorizationService = authorizationService;
  }

  @Operation(
      summary = "Listar registros de auditoria",
      description =
          "Filtre por item (exige enxergar o inventário dele) ou por setor de destino (exige enxergar o setor). Sem nenhum filtro, só o administrador pode listar. Se informar os dois, vale o filtro por item para a checagem de acesso.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Registros de auditoria.",
        content =
            @Content(array = @ArraySchema(schema = @Schema(implementation = ItemAuditDto.class)))),
    @ApiResponse(responseCode = "404", description = "Item não encontrado.", content = @Content)
  })
  /**
   * Por item: quem enxerga o inventário dele. Por setor de destino: quem enxerga o setor. Sem
   * filtro: apenas administrador.
   */
  @GetMapping
  public ResponseEntity<List<ItemAuditDto>> list(
      @Parameter(description = "Filtra pelo item auditado.") @RequestParam(required = false)
          Long itemId,
      @Parameter(description = "Filtra pelo setor dono do inventário do item.")
          @RequestParam(required = false)
          Long targetSectorId,
      HttpServletRequest request) {
    long userId = Long.parseLong(request.getAttribute("id_user").toString());
    if (itemId != null) {
      itemService.findAccessibleItem(itemId, userId);
    } else if (targetSectorId != null) {
      authorizationService.requireViewSector(userId, targetSectorId);
    } else {
      authorizationService.requireAdmin(userId);
    }
    List<ItemAuditDto> dtos =
        itemAuditService.listAudits(itemId, targetSectorId).stream()
            .map(ItemAuditDto::from)
            .toList();
    return ResponseEntity.ok(dtos);
  }
}
