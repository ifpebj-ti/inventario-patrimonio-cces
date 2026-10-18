package clp.inventory.service;

import clp.inventory.model.Profile;
import clp.inventory.repository.ProfileRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Catálogo de perfis definido pelas migrations, disponível apenas para consulta por ADMIN. */
@Service
@Transactional(readOnly = true)
public class ProfileService {
  private final ProfileRepository profileRepository;
  private final AuthorizationService authorizationService;

  public ProfileService(
      ProfileRepository profileRepository, AuthorizationService authorizationService) {
    this.profileRepository = profileRepository;
    this.authorizationService = authorizationService;
  }

  public Profile findProfileById(long id, long actingUserId) {
    authorizationService.requirePermission(actingUserId, "ADMIN");
    return profileRepository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("Profile not found with id: " + id));
  }

  /**
   * Catálogo de leitura para quem gerencia usuários (admin e gestor). A restrição do que o gestor
   * pode atribuir é aplicada ao cadastrar e ao alterar o perfil de um usuário.
   */
  public List<Profile> listAllProfiles(long actingUserId) {
    authorizationService.requireAnyPermission(
        actingUserId, AuthorizationService.ADMIN, AuthorizationService.MANAGE_SECTOR);
    return profileRepository.findAll();
  }

  public void rejectChanges(long actingUserId) {
    authorizationService.requirePermission(actingUserId, "ADMIN");
    throw new ResponseStatusException(
        HttpStatus.METHOD_NOT_ALLOWED,
        "Perfis e permissões são definidos pelo sistema e estão disponíveis somente para consulta.");
  }
}
