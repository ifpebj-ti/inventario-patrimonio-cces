package clp.inventory.service.auth;

import clp.inventory.providers.SessionToken;
import clp.inventory.repository.RevokedTokenRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

  private final RevokedTokenRepository revokedTokens;

  public SessionService(RevokedTokenRepository revokedTokens) {
    this.revokedTokens = revokedTokens;
  }

  /**
   * Encerra só a sessão deste token: o jti passa a ser recusado pelo SecurityFilter até o token
   * expirar. Repetir o logout do mesmo token não falha. Aproveita para apagar os tokens que já
   * expiraram, que o exp recusa de qualquer forma, e assim a tabela não cresce.
   */
  @Transactional
  public void logout(SessionToken token) {
    revokedTokens.deleteExpired(Instant.now());
    revokedTokens.revoke(token.jti(), token.userId(), token.expiresAt());
  }

  public boolean isRevoked(SessionToken token) {
    return revokedTokens.existsById(token.jti());
  }
}
