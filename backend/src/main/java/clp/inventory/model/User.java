package clp.inventory.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "im_user")
public class User {

  @Id
  @Column(name = "id")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "im_user_id")
  @SequenceGenerator(name = "im_user_id", sequenceName = "im_user_id", allocationSize = 1)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(unique = true, nullable = false)
  private String email;

  // O sub do Google. Não é consultado para autenticar — quem autentica é a
  // assinatura do ID token. Usuários comuns entram pelo Google; a conta de sistema usa senha.
  // Contas pré-cadastradas vinculam o sub na primeira entrada verificada.
  @Column(name = "google_id", unique = true)
  @JsonIgnore
  private String googleId;

  @Column(name = "is_system_user", nullable = false)
  @JsonIgnore
  private boolean systemUser = false;

  // Conta desativada não entra no sistema; o histórico (inventários criados) é preservado.
  @Column(name = "active", nullable = false)
  @JsonIgnore
  private boolean active = true;

  @JsonIgnore
  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  @Column(name = "password_hash")
  @JsonIgnore
  private String passwordHash;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private LocalDateTime updatedAt;

  // Administrador: acesso total, em todos os setores. É um poder global, fora dos cargos (que valem
  // por setor).
  @Column(name = "is_administrator", nullable = false)
  @JsonIgnore
  private boolean administrator = false;

  public User() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  @JsonIgnore
  public String getGoogleId() {
    return googleId;
  }

  public void setGoogleId(String googleId) {
    this.googleId = googleId;
  }

  @JsonIgnore
  public boolean isSystemUser() {
    return systemUser;
  }

  public void setSystemUser(boolean systemUser) {
    this.systemUser = systemUser;
  }

  @JsonIgnore
  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  @JsonIgnore
  public boolean isAdministrator() {
    return administrator;
  }

  public void setAdministrator(boolean administrator) {
    this.administrator = administrator;
  }
}
