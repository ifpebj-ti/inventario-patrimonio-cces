package clp.inventory.controller;

import clp.inventory.dto.ItemAuditDto;
import clp.inventory.service.ItemAuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/item-audits")
@CrossOrigin(origins = "*")
public class ItemAuditController {

    private final ItemAuditService itemAuditService;

    public ItemAuditController(ItemAuditService itemAuditService) {
        this.itemAuditService = itemAuditService;
    }

    @GetMapping
    public ResponseEntity<List<ItemAuditDto>> list(
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) Long targetSectorId
    ) {
        List<ItemAuditDto> dtos = itemAuditService.listAudits(itemId, targetSectorId)
                .stream().map(ItemAuditDto::from).toList();
        return ResponseEntity.ok(dtos);
    }
}
