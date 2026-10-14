package clp.inventory.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "im_inventory")
public class Inventory {

  @Id
  @Column(name = "id")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "im_inventory_id")
  @SequenceGenerator(name = "im_inventory_id", sequenceName = "im_inventory_id", allocationSize = 1)
  private long id;

  @Column(nullable = false)
  private String name;

  private String description;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private LocalDateTime updatedAt;

  // Prazo escolhido pelo usuário; é usado para identificar inventários atrasados.
  @Column(name = "planned_end_date")
  private LocalDate plannedEndDate;

  @ManyToOne
  @JoinColumn(name = "id_user", nullable = false)
  private User user;

  // Setor responsável pelo inventário, utilizado nos filtros e indicadores do dashboard.
  @ManyToOne
  @JoinColumn(name = "id_sector")
  private Sector sector;

  @OneToMany(
      mappedBy = "inventory",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private List<Item> items = new ArrayList<>();

  public Inventory() {}

  public User User() {
    return user;
  }

  public Inventory(
      String name, String description, LocalDate plannedEndDate, User user, Sector sector) {
    this.name = name;
    this.description = description;
    this.plannedEndDate = plannedEndDate;
    this.user = user;
    this.sector = sector;
  }

  public Long id() {
    return id;
  }

  public String name() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String description() {
    return description;
  }

  public LocalDateTime createdAt() {
    return createdAt;
  }

  // Data de alteração exposta para permitir ordenação na lista de inventários.
  public LocalDateTime updatedAt() {
    return updatedAt;
  }

  public LocalDate plannedEndDate() {
    return plannedEndDate;
  }

  public void setPlannedEndDate(LocalDate plannedEndDate) {
    this.plannedEndDate = plannedEndDate;
  }

  public Sector sector() {
    return sector;
  }

  public List<Item> items() {
    return items;
  }

  public void addItem(Item item) {
    item.setInventory(this);
    items.add(item);
  }

  public void removeItem(Item item) {
    item.setInventory(this);
    items.remove(item);
  }
}
