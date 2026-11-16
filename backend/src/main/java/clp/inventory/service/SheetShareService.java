package clp.inventory.service;

import clp.inventory.dto.EmailRequestDto;
import clp.inventory.model.Inventory;
import clp.inventory.model.User;
import clp.inventory.service.generateSheet.SheetBuilderService;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tirar a planilha patrimonial do sistema: baixar (export) ou enviar por e-mail. É um dado sensível
 * (responsável e valor de todos os bens), então só administrador e gestor do setor podem, o e-mail
 * só vai para domínio permitido e com limite de envios, e cada ato (também os recusados) fica na
 * trilha de auditoria.
 *
 * <p>Sem {@code @Transactional} de propósito: a auditoria de um ato recusado precisa ser gravada
 * mesmo quando o método termina lançando a exceção.
 */
@Service
public class SheetShareService {

  static final String ATTACHMENT_NAME = "patrimonio.xlsx";

  private final ItemService itemService;
  private final SheetBuilderService sheetBuilderService;
  private final EmailService emailService;
  private final SheetEmailPolicy emailPolicy;
  private final SheetEmailLimiter emailLimiter;
  private final InventoryAuditService inventoryAuditService;
  private final AuthorizationService authorizationService;
  private final UserService userService;

  public SheetShareService(
      ItemService itemService,
      SheetBuilderService sheetBuilderService,
      EmailService emailService,
      SheetEmailPolicy emailPolicy,
      SheetEmailLimiter emailLimiter,
      InventoryAuditService inventoryAuditService,
      AuthorizationService authorizationService,
      UserService userService) {
    this.itemService = itemService;
    this.sheetBuilderService = sheetBuilderService;
    this.emailService = emailService;
    this.emailPolicy = emailPolicy;
    this.emailLimiter = emailLimiter;
    this.inventoryAuditService = inventoryAuditService;
    this.authorizationService = authorizationService;
    this.userService = userService;
  }

  /** Planilha do inventário para download. Exige gerir o inventário. */
  public byte[] exportSheet(long inventoryId, long userId) throws IOException {
    Inventory inventory = itemService.requireInventoryManage(inventoryId, userId);
    byte[] sheet = buildSheet(inventory, userId, InventoryAuditService.ACTION_EXPORT_SHEET, null);
    audit(inventory, userId, InventoryAuditService.ACTION_EXPORT_SHEET, null, success(), null);
    return sheet;
  }

  /**
   * Envia a planilha por e-mail. 400 se o destinatário não é de um domínio permitido, 429 se o
   * usuário passou do limite de envios e 502 se o servidor de e-mail não aceitou a mensagem.
   */
  public void sendSheetByEmail(long inventoryId, EmailRequestDto request, long userId)
      throws IOException {
    Inventory inventory = itemService.requireInventoryManage(inventoryId, userId);
    String action = InventoryAuditService.ACTION_SEND_SHEET_EMAIL;
    String recipient = request.email();

    if (!emailPolicy.isAllowed(recipient)) {
      audit(
          inventory, userId, action, recipient, InventoryAuditService.STATUS_REJECTED_DOMAIN, null);
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "O destinatário não é de um domínio permitido.");
    }

    Optional<Duration> wait = emailLimiter.tryAcquire(userId);
    if (wait.isPresent()) {
      audit(inventory, userId, action, recipient, InventoryAuditService.STATUS_RATE_LIMITED, null);
      throw new TooManyRequestsException(
          "Muitos envios em pouco tempo. Tente de novo mais tarde.", wait.get());
    }

    byte[] sheet = buildSheet(inventory, userId, action, recipient);
    try {
      emailService.sendEmailWithAttachment(
          recipient, request.subject(), request.message(), ATTACHMENT_NAME, sheet);
    } catch (EmailDeliveryException e) {
      audit(inventory, userId, action, recipient, InventoryAuditService.STATUS_FAILED, null);
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Não foi possível enviar o e-mail.");
    }
    audit(inventory, userId, action, recipient, success(), null);
  }

  private byte[] buildSheet(Inventory inventory, long userId, String action, String recipient)
      throws IOException {
    try {
      return sheetBuilderService.createAllItemsSheet(inventory.id());
    } catch (IOException | RuntimeException e) {
      audit(inventory, userId, action, recipient, InventoryAuditService.STATUS_FAILED, null);
      throw e;
    }
  }

  private static String success() {
    return InventoryAuditService.STATUS_SUCCESS;
  }

  private void audit(
      Inventory inventory,
      long userId,
      String action,
      String recipient,
      String status,
      String details) {
    User actor = userService.findUserById(userId);
    // O setor do autor é o do inventário quando a pessoa está alocada a ele; o administrador não
    // pertence a um setor, então fica nulo.
    Long sectorId = inventory.sector() == null ? null : inventory.sector().id();
    Long actorSectorId =
        sectorId != null && authorizationService.isAllocatedTo(userId, sectorId) ? sectorId : null;
    inventoryAuditService.record(
        actor, actorSectorId, inventory.id(), action, recipient, status, details);
  }
}
