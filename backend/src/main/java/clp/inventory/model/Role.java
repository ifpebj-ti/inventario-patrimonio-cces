package clp.inventory.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Cargo: um conjunto de permissões que vale no setor em que o usuário está alocado ({@link
 * SectorAllocation}). O mesmo usuário pode ter cargos diferentes em setores diferentes.
 */
@Entity
@Table(name = "im_role")
public class Role {

  @Id
  @Column(name = "id")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "im_role_id")
  @SequenceGenerator(name = "im_role_id", sequenceName = "im_role_id", allocationSize = 1)
  private long id;

  @Column(nullable = false, unique = true, length = 100)
  private String name;

  private String description;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private LocalDateTime updatedAt;

  // EAGER: a autorização lê as permissões do cargo a cada requisição, fora de transação.
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "im_role_permission",
      joinColumns = @JoinColumn(name = "id_role"),
      inverseJoinColumns = @JoinColumn(name = "id_permission"))
  private Set<Permission> permissions = new HashSet<>();

  public Role() {}

  public Role(String name, String description) {
    this.name = name;
    this.description = description;
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

  public String description() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public LocalDateTime createdAt() {
    return createdAt;
  }

  public LocalDateTime updatedAt() {
    return updatedAt;
  }

  public Set<Permission> permissions() {
    return permissions;
  }

  public boolean hasPermission(String permissionName) {
    return permissions.stream().anyMatch(p -> p.name().equals(permissionName));
  }

  public void setPermissions(Set<Permission> newPermissions) {
    permissions.clear();
    permissions.addAll(newPermissions);
  }
}
