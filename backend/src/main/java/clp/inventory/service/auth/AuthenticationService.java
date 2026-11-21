package clp.inventory.service.auth;

import clp.inventory.dto.AuthResponseDTO;
import clp.inventory.dto.GoogleAuthDto;
import clp.inventory.dto.LoginDto;
import clp.inventory.model.User;
import clp.inventory.providers.GoogleTokenProvider;
import clp.inventory.providers.JWTProvider;
import clp.inventory.repository.UserRepository;
import clp.inventory.service.AuthenticatedUserService;
import clp.inventory.service.UserService;
import java.util.Locale;
import javax.security.sasl.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

  private final UserService userService;
  private final AuthenticatedUserService authenticatedUserService;
  private final GoogleTokenProvider googleTokenProvider;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JWTProvider jwtProvider;
  private final String decoyPasswordHash;

  public AuthenticationService(
      UserService userService,
      AuthenticatedUserService authenticatedUserService,
      GoogleTokenProvider googleTokenProvider,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JWTProvider jwtProvider) {
    this.userService = userService;
    this.authenticatedUserService = authenticatedUserService;
    this.googleTokenProvider = googleTokenProvider;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtProvider = jwtProvider;
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

  private String issueToken(User user) {
    return jwtProvider.issue(user.getId(), user.getTokenVersion());
  }
}
