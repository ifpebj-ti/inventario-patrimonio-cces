package clp.inventory.service;

import clp.inventory.model.InventoryAudit;
import clp.inventory.model.User;
import clp.inventory.repository.InventoryAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Trilha dos atos sobre o inventario como um todo (exportar e enviar a planilha por e-mail, reabrir
 * o inventario) e da edicao dos dados de um item, que fica aqui e nao na trilha de validacao para
 * nao prender o item por chave estrangeira.
 */
@Service
public class InventoryAuditService {

  public static final String ACTION_EXPORT_SHEET = "EXPORT_SHEET";
  public static final String ACTION_SEND_SHEET_EMAIL = "SEND_SHEET_EMAIL";
  public static final String ACTION_REOPEN_INVENTORY = "REOPEN_INVENTORY";
  public static final String ACTION_UPDATE_ITEM = "UPDATE_ITEM";

  public static final String STATUS_SUCCESS = "SUCCESS";
  public static final String STATUS_FAILED = "FAILED";
  public static final String STATUS_REJECTED_DOMAIN = "REJECTED_DOMAIN";
  public static final String STATUS_RATE_LIMITED = "RATE_LIMITED";

  private static final Logger log = LoggerFactory.getLogger(InventoryAuditService.class);

  private final InventoryAuditRepository inventoryAuditRepository;

  public InventoryAuditService(InventoryAuditRepository inventoryAuditRepository) {
    this.inventoryAuditRepository = inventoryAuditRepository;
  }

  /**
   * Grava o ato. Um erro ao gravar a auditoria não pode derrubar nem mascarar a ação principal, mas
   * também não passa em silêncio: vai para o log.
   */
  public void record(
      User actorUser,
      Long actorSectorId,
      Long inventoryId,
      String actionType,
      String recipient,
      String status,
      String details) {
    record(actorUser, actorSectorId, inventoryId, null, actionType, recipient, status, details);
  }

  /** Igual ao anterior, para um ato sobre um item (o id do item vai na linha). */
  public void record(
      User actorUser,
      Long actorSectorId,
      Long inventoryId,
      Long itemId,
      String actionType,
      String recipient,
      String status,
      String details) {
    try {
      inventoryAuditRepository.save(
          new InventoryAudit(
              actorUser,
              actorSectorId,
              inventoryId,
              itemId,
              actionType,
              recipient,
              status,
              details));
    } catch (RuntimeException e) {
      log.error(
          "Falha ao gravar a auditoria (action={}, inventory={}, status={})",
          actionType,
          inventoryId,
          status,
          e);
    }
  }
}
