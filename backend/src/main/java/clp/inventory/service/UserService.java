package clp.inventory.service;

import clp.inventory.dto.GoogleUserInfo;
import clp.inventory.model.User;
import clp.inventory.repository.SectorAllocationRepository;
import clp.inventory.repository.UserRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import javax.security.sasl.AuthenticationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

  // Vazio libera qualquer domínio. Cada entrada casa com o domínio e seus subdomínios.
  @Value("${google.oauth.allowed-domains:}")
  private List<String> allowedDomains = List.of();

  private final UserRepository userRepository;
  private final SectorAllocationRepository sectorAllocationRepository;
  private final AuthorizationService authorizationService;

  @Value("${security.token.secret}")
  private String secretKey;

  public UserService(
      UserRepository userRepository,
      SectorAllocationRepository sectorAllocationRepository,
      AuthorizationService authorizationService) {
    this.userRepository = userRepository;
    this.sectorAllocationRepository = sectorAllocationRepository;
    this.authorizationService = authorizationService;
  }

  /**
   * Localiza o usuário pelo e-mail verificado do Google ou cria a conta. Esta é a única forma de um
   * usuário nascer: sem alocação em nenhum setor e sem ser administrador, ou seja, sem acesso até
   * que alguém o aloque em um setor com um cargo.
   */
  @Transactional
  public User findOrCreateGoogleUser(GoogleUserInfo googleUser) throws AuthenticationException {
    // Normaliza o e-mail antes de localizar a conta; o banco também garante unicidade sem caixa.
    String email = googleUser.email().trim().toLowerCase(Locale.ROOT);

    Optional<User> existing = userRepository.findByEmailForUpdate(email);
    if (existing.isPresent()) {
      // A conta de sistema só entra por usuário e senha, nunca pelo Google.
      if (existing.get().isSystemUser()) {
        throw new AuthenticationException("System account cannot sign in with Google");
      }
      if (!existing.get().isActive()) {
        throw new AuthenticationException("Account is deactivated");
      }
      if (!googleUser.sub().equals(existing.get().getGoogleId())) {
        throw new AuthenticationException("Google account does not match the registered identity");
      }
      return existing.get();
    }

    // Porta de entrada: só quem ainda não tem conta passa por aqui.
    if (!isDomainAllowed(email)) {
      throw new AuthenticationException("Domain not allowed: " + email);
    }

    User user = new User();
    user.setName(googleUser.name());
    user.setEmail(email);
    user.setGoogleId(googleUser.sub());
    // Sem alocação e sem ser administrador de propósito: quem entra pela primeira vez não tem
    // acesso
    // a nenhum setor até que um administrador (ou o gestor do setor) o aloque com um cargo.

    return userRepository.save(user);
  }

  private boolean isDomainAllowed(String email) {
    if (allowedDomains.isEmpty()) {
      return true;
    }

    String domain = email.substring(email.indexOf('@') + 1);

    // O "." antes do domínio permitido é o que impede @fakeifpe.edu.br de passar,
    // enquanto libera os subdomínios @discente. e @docente.
    return allowedDomains.stream()
        .map(allowed -> allowed.trim().toLowerCase(Locale.ROOT))
        .filter(allowed -> !allowed.isEmpty())
        .anyMatch(allowed -> domain.equals(allowed) || domain.endsWith("." + allowed));
  }

  public User getCurrentUser(String token) {
    token = token.replace("Bearer ", "");
    Algorithm algorithm = Algorithm.HMAC256(secretKey);
    String uid = JWT.require(algorithm).withIssuer("inventory").build().verify(token).getSubject();

    var user = userRepository.findById(Long.parseLong(uid));

    return user.orElseThrow(() -> new RuntimeException("User not found"));
  }

  public User findUserById(long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
  }

  /**
   * Concede ou retira o papel de administrador. Só o administrador faz isso, e nunca na própria
   * conta: como quem age é sempre um administrador ativo e diferente do alvo, o sistema não fica
   * sem administrador.
   */
  @Transactional
  public User setAdministrator(long userId, boolean administrator, long actingUserId) {
    authorizationService.requireAdmin(actingUserId);
    // A conta de sistema responde como se não existisse: ela precisa continuar administradora.
    User user =
        userRepository
            .findById(userId)
            .filter(found -> !found.isSystemUser())
            .orElseThrow(() -> new NoSuchElementException("User not found with id: " + userId));
    if (user.getId() == actingUserId) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Você não pode alterar o seu próprio papel de administrador.");
    }
    user.setAdministrator(administrator);
    return userRepository.save(user);
  }

  /**
   * Desativar impede o acesso sem apagar o histórico; ativar devolve o acesso. Só o administrador
   * ativa ou desativa: a conta vale para todos os setores do usuário.
   */
  @Transactional
  public User setActive(long userId, boolean active, long actingUserId) {
    authorizationService.requireAdmin(actingUserId);
    // A conta de sistema responde como se não existisse.
    User user =
        userRepository
            .findById(userId)
            .filter(found -> !found.isSystemUser())
            .orElseThrow(() -> new NoSuchElementException("User not found with id: " + userId));
    if (user.getId() == actingUserId) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Você não pode " + (active ? "ativar" : "desativar") + " a sua própria conta.");
    }
    user.setActive(active);
    return userRepository.save(user);
  }

  /**
   * Admin lista todos; gestor lista os usuários dos setores em que seu cargo tem MANAGE_SECTOR. Com
   * {@code roleId}, só quem tem esse cargo em algum setor (no escopo de quem pergunta).
   */
  public List<User> listUsers(Long roleId, long actingUserId) {
    authorizationService.requireAdminOrSectorManager(actingUserId);
    if (authorizationService.isAdmin(actingUserId)) {
      return roleId != null
          ? sectorAllocationRepository.findUsersWithRole(roleId)
          : userRepository.findAllBySystemUserFalse();
    }
    Set<Long> managed = authorizationService.managedSectorIds(actingUserId);
    return roleId != null
        ? sectorAllocationRepository.findUsersAllocatedToAnyWithRole(managed, roleId)
        : sectorAllocationRepository.findUsersAllocatedToAny(managed);
  }
}
