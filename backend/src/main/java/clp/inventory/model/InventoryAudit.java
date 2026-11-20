package clp.inventory.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Registro de um ato administrativo que não tem item (hoje, exportar a planilha do inventário e
 * enviá-la por e-mail). Guarda também os atos recusados. O setor do autor e o inventário são ids
 * simples, sem chave estrangeira: o histórico não pode impedir excluir o setor nem o inventário.
 */
@Entity
@Table(name = "im_inventory_audit")
public class InventoryAudit {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "im_inventory_audit_id")
  @SequenceGenerator(
      name = "im_inventory_audit_id",
      sequenceName = "im_inventory_audit_id",
      allocationSize = 1)
  private long id;

  @ManyToOne
  @JoinColumn(name = "id_actor_user", nullable = false)
  private User actorUser;

  // Nulo para o administrador, que não pertence a um setor.
  @Column(name = "id_actor_sector")
  private Long actorSectorId;

  @Column(name = "id_inventory")
  private Long inventoryId;

  // Item afetado, quando o ato é sobre um item. Id simples, sem chave estrangeira.
  @Column(name = "id_item")
  private Long itemId;

  @Column(name = "action_type", nullable = false, length = 50)
  private String actionType;

  @Column(length = 254)
  private String recipient;

  @Column(nullable = false, length = 50)
  private String status;

  @Column(length = 1000)
  private String details;

  @CreationTimestamp
  @Column(name = "occurred_at", nullable = false, updatable = false)
  private LocalDateTime occurredAt;

  public InventoryAudit() {}

  public InventoryAudit(
      User actorUser,
      Long actorSectorId,
      Long inventoryId,
      String actionType,
      String recipient,
      String status,
      String details) {
    this(actorUser, actorSectorId, inventoryId, null, actionType, recipient, status, details);
  }

  public InventoryAudit(
      User actorUser,
      Long actorSectorId,
      Long inventoryId,
      Long itemId,
      String actionType,
      String recipient,
      String status,
      String details) {
    this.actorUser = actorUser;
    this.actorSectorId = actorSectorId;
    this.inventoryId = inventoryId;
    this.itemId = itemId;
    this.actionType = actionType;
    this.recipient = recipient;
    this.status = status;
    this.details = details;
  }

  public long id() {
    return id;
  }

  public User actorUser() {
    return actorUser;
  }

  public Long actorSectorId() {
    return actorSectorId;
  }

  public Long inventoryId() {
    return inventoryId;
  }

  public Long itemId() {
    return itemId;
  }

  public String actionType() {
    return actionType;
  }

  public String recipient() {
    return recipient;
  }

  public String status() {
    return status;
  }

  public String details() {
    return details;
  }

  public LocalDateTime occurredAt() {
    return occurredAt;
  }
}
