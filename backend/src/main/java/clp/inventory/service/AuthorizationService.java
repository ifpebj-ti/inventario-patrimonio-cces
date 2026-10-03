package clp.inventory.service;

import clp.inventory.model.User;
import clp.inventory.repository.SectorAllocationRepository;
import clp.inventory.repository.UserRepository;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthorizationService {

  private final UserRepository userRepository;
  private final SectorAllocationRepository sectorAllocationRepository;

  public AuthorizationService(
      UserRepository userRepository, SectorAllocationRepository sectorAllocationRepository) {
    this.userRepository = userRepository;
    this.sectorAllocationRepository = sectorAllocationRepository;
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

  public void requireSectorPermission(long actingUserId, long sectorId) {
    if (hasPermission(actingUserId, "ADMIN")) {
      return;
    }
    boolean managesSector =
        hasPermission(actingUserId, "MANAGE_SECTOR")
            && sectorAllocationRepository.existsByUser_IdAndSector_Id(actingUserId, sectorId);
    if (!managesSector) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Requer ADMIN ou MANAGE_SECTOR no setor informado");
    }
  }

  private User requireUser(long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao encontrado"));
  }
}
