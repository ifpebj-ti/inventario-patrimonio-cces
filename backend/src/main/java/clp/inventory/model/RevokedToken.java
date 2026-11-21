package clp.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Token encerrado antes de expirar (logout). Guarda só o id do token e até quando ele valeria: o
 * exp do próprio JWT recusa o token depois disso, então a linha pode ser apagada.
 */
@Entity
@Table(name = "im_revoked_token")
public class RevokedToken {

  @Id
  @Column(name = "jti", length = 64)
  private String jti;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  protected RevokedToken() {}

  public RevokedToken(String jti, Long userId, Instant expiresAt) {
    this.jti = jti;
    this.userId = userId;
    this.expiresAt = expiresAt;
  }

  public String getJti() {
    return jti;
  }

  public Long getUserId() {
    return userId;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
