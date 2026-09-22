package clp.inventory.controller;

import clp.inventory.dto.ItemAuditDto;
import clp.inventory.service.AuthorizationService;
import clp.inventory.service.ItemAuditService;
import clp.inventory.service.ItemService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

  /**
   * Por item: quem enxerga o inventário dele. Por setor de destino: quem enxerga o setor. Sem
   * filtro: apenas administrador.
   */
  @GetMapping
  public ResponseEntity<List<ItemAuditDto>> list(
      @RequestParam(required = false) Long itemId,
      @RequestParam(required = false) Long targetSectorId,
      HttpServletRequest request) {
    long userId = Long.parseLong(request.getAttribute("id_user").toString());
    if (itemId != null) {
      itemService.findAccessibleItem(itemId, userId);
    } else if (targetSectorId != null) {
      authorizationService.requireViewSector(userId, targetSectorId);
    } else {
      authorizationService.requirePermission(userId, AuthorizationService.ADMIN);
    }
    List<ItemAuditDto> dtos =
        itemAuditService.listAudits(itemId, targetSectorId).stream()
            .map(ItemAuditDto::from)
            .toList();
    return ResponseEntity.ok(dtos);
  }
}
