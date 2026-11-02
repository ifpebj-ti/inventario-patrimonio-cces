package clp.inventory.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Sala de um setor. O nome é único dentro do setor, sem diferenciar caixa, acento e espaços. */
@Entity
@Table(name = "im_room")
public class Room {

  @Id
  @Column(name = "id")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "im_room_id")
  @SequenceGenerator(name = "im_room_id", sequenceName = "im_room_id", allocationSize = 1)
  private long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "id_sector", nullable = false)
  private Sector sector;

  @Column(nullable = false)
  private String name;

  @Column(name = "normalized_name", nullable = false)
  private String normalizedName;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public Room() {}

  public Room(Sector sector, String name, String normalizedName) {
    this.sector = sector;
    this.name = name;
    this.normalizedName = normalizedName;
  }

  public long id() {
    return id;
  }

  public Sector sector() {
    return sector;
  }

  public String name() {
    return name;
  }

  public String normalizedName() {
    return normalizedName;
  }

  public void rename(String name, String normalizedName) {
    this.name = name;
    this.normalizedName = normalizedName;
  }
}
