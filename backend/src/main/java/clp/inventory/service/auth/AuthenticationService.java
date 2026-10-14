package clp.inventory.service.auth;

import clp.inventory.dto.AuthResponseDTO;
import clp.inventory.dto.GoogleAuthDto;
import clp.inventory.dto.LoginDto;
import clp.inventory.model.User;
import clp.inventory.providers.GoogleTokenProvider;
import clp.inventory.repository.UserRepository;
import clp.inventory.service.AuthenticatedUserService;
import clp.inventory.service.UserService;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import javax.security.sasl.AuthenticationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

  @Value("${security.token.secret}")
  private String secretKey;

  private final UserService userService;
  private final AuthenticatedUserService authenticatedUserService;
  private final GoogleTokenProvider googleTokenProvider;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final String decoyPasswordHash;

  public AuthenticationService(
      UserService userService,
      AuthenticatedUserService authenticatedUserService,
      GoogleTokenProvider googleTokenProvider,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder) {
    this.userService = userService;
    this.authenticatedUserService = authenticatedUserService;
    this.googleTokenProvider = googleTokenProvider;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.decoyPasswordHash = passwordEncoder.encode("decoy-password-for-timing");
  }

  /**
   * Login da conta de sistema. O identificador de login é a coluna email. Quando não há conta
   * utilizável, a senha é conferida mesmo assim contra um hash descartável, para que "usuário
   * inexistente" e "senha errada" levem o mesmo tempo.
   */
  public AuthResponseDTO authenticateWithPassword(LoginDto loginDto)
      throws AuthenticationException {
    String identifier = loginDto.username().trim().toLowerCase(Locale.ROOT);
    User user =
        userRepository
            .findByEmail(identifier)
            .filter(u -> u.isSystemUser() && u.getPasswordHash() != null)
            .orElse(null);

    String hash = user != null ? user.getPasswordHash() : decoyPasswordHash;
    boolean matches = passwordEncoder.matches(loginDto.password(), hash);
    if (user == null || !matches) {
      throw new AuthenticationException("Invalid credentials");
    }

    return new AuthResponseDTO(issueToken(user), authenticatedUserService.getById(user.getId()));
  }

  public AuthResponseDTO authenticateWithGoogle(GoogleAuthDto googleAuthDto)
      throws AuthenticationException {
    var googleUser = googleTokenProvider.verify(googleAuthDto.credential());
    var user = userService.findOrCreateGoogleUser(googleUser);

    return new AuthResponseDTO(issueToken(user), authenticatedUserService.getById(user.getId()));
  }

  /**
   * Emite o token da aplicação. O issuer "inventory" é obrigatório: o SecurityFilter não o confere,
   * mas AuthenticationUtils e getCurrentUser conferem, então um token sem ele passaria pelo filtro
   * e só quebraria depois, dentro do controller.
   */
  private String issueToken(User user) {
    Algorithm algorithm = Algorithm.HMAC256(secretKey);

    return JWT.create()
        .withIssuer("inventory")
        .withSubject(user.getId().toString())
        .withExpiresAt(Instant.now().plus(Duration.ofHours(24)))
        .sign(algorithm);
  }
}
