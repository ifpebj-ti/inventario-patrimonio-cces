package clp.inventory.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "im_sector")
public class Sector {

  @Id
  @Column(name = "id")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "im_sector_id")
  @SequenceGenerator(name = "im_sector_id", sequenceName = "im_sector_id", allocationSize = 1)
  private long id;

  @Column(nullable = false, unique = true)
  private String name;

  @Column(length = 100)
  private String code;

  @Column(nullable = false)
  private boolean active = true;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private LocalDateTime updatedAt;

  public Sector() {}

  public Sector(String name, String code) {
    this.name = name;
    this.code = code;
  }

  public long id() {
    return id;
  }

  public String name() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String code() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public boolean active() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public LocalDateTime createdAt() {
    return createdAt;
  }

  public LocalDateTime updatedAt() {
    return updatedAt;
  }
}
