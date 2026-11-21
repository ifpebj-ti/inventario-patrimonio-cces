package clp.inventory.providers;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JWTProvider {

  private static final Logger log = LoggerFactory.getLogger(JWTProvider.class);

  static final String ISSUER = "inventory";
  static final String VERSION_CLAIM = "ver";

  @Value("${security.token.secret}")
  private String secretKey;

  @Value("${security.token.ttl:PT8H}")
  private Duration ttl;

  /**
   * Emite o token da aplicação: id único (jti, para o logout encerrar só esta sessão), versão das
   * sessões da conta (ver, para trocar a credencial encerrar todas) e emissão (iat).
   *
   * <p>O issuer é obrigatório: AuthenticationUtils e getCurrentUser o conferem, então um token sem
   * ele passaria pelo filtro e só quebraria depois, dentro do controller.
   */
  public String issue(long userId, int version) {
    Instant now = Instant.now();

    return JWT.create()
        .withIssuer(ISSUER)
        .withSubject(Long.toString(userId))
        .withJWTId(UUID.randomUUID().toString())
        .withClaim(VERSION_CLAIM, version)
        .withIssuedAt(now)
        .withExpiresAt(now.plus(ttl))
        .sign(Algorithm.HMAC256(secretKey));
  }

  /**
   * Confere assinatura, issuer e validade. Token sem jti ou sem versão também é recusado: sem eles
   * a sessão não teria como ser revogada, e todo token emitido por este servidor os traz.
   */
  public Optional<SessionToken> parse(String token) {
    token = token.replace("Bearer ", "");
    Algorithm algorithm = Algorithm.HMAC256(secretKey);

    try {
      // O issuer precisa ser conferido aqui também: sem isso o AuthenticationUtils,
      // que confere, rejeitaria dentro do controller um token já aceito pelo filtro.
      DecodedJWT decoded = JWT.require(algorithm).withIssuer(ISSUER).build().verify(token);
      var version = decoded.getClaim(VERSION_CLAIM);

      if (decoded.getSubject() == null
          || decoded.getId() == null
          || version.isMissing()
          || version.isNull()
          || decoded.getExpiresAtAsInstant() == null) {
        log.debug("Token sem jti, versão ou validade.");
        return Optional.empty();
      }

      return Optional.of(
          new SessionToken(
              decoded.getSubject(),
              decoded.getId(),
              version.asInt(),
              decoded.getExpiresAtAsInstant()));
    } catch (JWTVerificationException e) {
      log.debug("Token inválido: {}", e.getMessage());
      return Optional.empty();
    }
  }
}
