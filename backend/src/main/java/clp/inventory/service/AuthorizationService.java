package clp.inventory.service;

import clp.inventory.model.User;
import clp.inventory.repository.UserRepository;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthorizationService {

  private final UserRepository userRepository;

  public AuthorizationService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public boolean hasPermission(long userId, String permissionName) {
    User user = requireUser(userId);
    return user.getProfile() != null
        && user.getProfile().permissions().stream()
            .anyMatch(p -> p.name().equals(permissionName));
  }

  public void requirePermission(long userId, String permissionName) {
    if (!hasPermission(userId, permissionName)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Requer a permissao " + permissionName);
    }
  }

  public void requireAnyPermission(long userId, String... permissionNames) {
    boolean allowed = Arrays.stream(permissionNames).anyMatch(p -> hasPermission(userId, p));
    if (!allowed) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Requer uma das permissoes: " + String.join(", ", permissionNames));
    }
  }

  public void requireSectorAssignmentPermission(
      long actingUserId, User targetUser, Long newSectorId) {
    if (hasPermission(actingUserId, "ADMIN")) {
      return;
    }
    Long relevantSectorId = newSectorId != null ? newSectorId : targetUser.getSectorId();
    User actingUser = requireUser(actingUserId);
    boolean managesSector =
        hasPermission(actingUserId, "MANAGE_SECTOR")
            && relevantSectorId != null
            && relevantSectorId.equals(actingUser.getSectorId());
    if (!managesSector) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Requer ADMIN ou MANAGE_SECTOR no setor de destino");
    }
  }

  private User requireUser(long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao encontrado"));
  }
}
