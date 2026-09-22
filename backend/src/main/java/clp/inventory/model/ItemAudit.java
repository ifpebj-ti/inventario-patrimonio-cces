package clp.inventory.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "im_item_audit")
public class ItemAudit {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "im_item_audit_id")
    @SequenceGenerator(name = "im_item_audit_id", sequenceName = "im_item_audit_id", allocationSize = 1)
    private long id;

    @ManyToOne
    @JoinColumn(name = "id_item", nullable = false)
    private Item item;

    @ManyToOne
    @JoinColumn(name = "id_inventory")
    private Inventory inventory;

    @ManyToOne
    @JoinColumn(name = "id_actor_user", nullable = false)
    private User actorUser;

    @ManyToOne
    @JoinColumn(name = "id_actor_sector")
    private Sector actorSector;

    @ManyToOne
    @JoinColumn(name = "id_target_sector")
    private Sector targetSector;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(length = 1000)
    private String details;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    public ItemAudit() {
    }

    public ItemAudit(Item item, Inventory inventory, User actorUser, Sector actorSector,
                      Sector targetSector, String eventType, String status, String details) {
        this.item = item;
        this.inventory = inventory;
        this.actorUser = actorUser;
        this.actorSector = actorSector;
        this.targetSector = targetSector;
        this.eventType = eventType;
        this.status = status;
        this.details = details;
    }

    public long id() {
        return id;
    }

    public Item item() {
        return item;
    }

    public Inventory inventory() {
        return inventory;
    }

    public User actorUser() {
        return actorUser;
    }

    public Sector actorSector() {
        return actorSector;
    }

    public Sector targetSector() {
        return targetSector;
    }

    public String eventType() {
        return eventType;
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
