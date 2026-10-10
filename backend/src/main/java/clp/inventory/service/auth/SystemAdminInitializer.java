package clp.inventory.service.auth;

import clp.inventory.model.Profile;
import clp.inventory.model.User;
import clp.inventory.repository.ProfileRepository;
import clp.inventory.repository.UserRepository;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mantém a conta de sistema (login por usuário e senha, sem Google) alinhada com o .env a cada
 * subida. O .env é a fonte da verdade: senha vazia desliga o login por senha, senha nova troca o
 * hash. Roda depois do Liquibase.
 */
@Component
public class SystemAdminInitializer implements ApplicationRunner {

  static final int MIN_PASSWORD_LENGTH = 12;
  static final String ADMIN_PROFILE = "ADMIN_ORGANIZATION";

  private static final Logger log = LoggerFactory.getLogger(SystemAdminInitializer.class);

  private final UserRepository userRepository;
  private final ProfileRepository profileRepository;
  private final PasswordEncoder passwordEncoder;
  private final String username;
  private final String password;

  public SystemAdminInitializer(
      UserRepository userRepository,
      ProfileRepository profileRepository,
      PasswordEncoder passwordEncoder,
      @Value("${system.admin.username:admin}") String username,
      @Value("${system.admin.password:}") String password) {
    this.userRepository = userRepository;
    this.profileRepository = profileRepository;
    this.passwordEncoder = passwordEncoder;
    this.username = username.trim().toLowerCase(Locale.ROOT);
    this.password = password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    Optional<User> existing = userRepository.findByEmail(username);

    if (existing.isPresent() && !existing.get().isSystemUser()) {
      log.error(
          "Já existe um usuário que não é de sistema com o identificador '{}'; a conta de sistema"
              + " não foi criada nem alterada.",
          username);
      return;
    }

    if (password.isEmpty()) {
      disablePasswordLogin(existing);
      return;
    }

    if (password.length() < MIN_PASSWORD_LENGTH) {
      log.error(
          "SYSTEM_ADMIN_PASSWORD tem menos de {} caracteres; a conta de sistema não foi criada nem"
              + " alterada.",
          MIN_PASSWORD_LENGTH);
      return;
    }

    Profile adminProfile =
        profileRepository
            .findByName(ADMIN_PROFILE)
            .orElseThrow(
                () -> new IllegalStateException("Perfil " + ADMIN_PROFILE + " não encontrado"));

    User user = existing.orElseGet(this::newSystemUser);
    if (user.getPasswordHash() == null
        || !passwordEncoder.matches(password, user.getPasswordHash())) {
      user.setPasswordHash(passwordEncoder.encode(password));
    }
    user.setProfile(adminProfile);
    userRepository.save(user);
    log.info("Conta de sistema '{}' pronta para login por senha.", username);
  }

  private void disablePasswordLogin(Optional<User> existing) {
    if (existing.isPresent() && existing.get().getPasswordHash() != null) {
      existing.get().setPasswordHash(null);
      userRepository.save(existing.get());
    }
    log.info("SYSTEM_ADMIN_PASSWORD vazio: login por senha desligado.");
  }

  private User newSystemUser() {
    User user = new User();
    user.setName("Administrador do Sistema");
    user.setEmail(username);
    user.setSystemUser(true);
    return user;
  }
}
