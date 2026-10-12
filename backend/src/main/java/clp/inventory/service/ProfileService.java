package clp.inventory.service;

import clp.inventory.dto.ProfileDto;
import clp.inventory.model.Permission;
import clp.inventory.model.Profile;
import clp.inventory.repository.PermissionRepository;
import clp.inventory.repository.ProfileRepository;
import clp.inventory.repository.UserRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Perfis exigem ADMIN em todas as operações. O perfil {@link Profile#ADMIN_PROFILE_NAME} é
 * protegido (não renomeia, não exclui e não perde a permissão ADMIN): a conta de sistema depende
 * dele e, sem ele, ninguém mais acessaria as rotas de administração nem o backend subiria.
 */
@Service
public class ProfileService {

  private static final String ADMIN_PERMISSION = "ADMIN";

  private final ProfileRepository profileRepository;
  private final PermissionRepository permissionRepository;
  private final UserRepository userRepository;
  private final AuthorizationService authorizationService;

  public ProfileService(
      ProfileRepository profileRepository,
      PermissionRepository permissionRepository,
      UserRepository userRepository,
      AuthorizationService authorizationService) {
    this.profileRepository = profileRepository;
    this.permissionRepository = permissionRepository;
    this.userRepository = userRepository;
    this.authorizationService = authorizationService;
  }

  @Transactional
  public Profile createProfile(ProfileDto dto, long actingUserId) {
    authorizationService.requirePermission(actingUserId, ADMIN_PERMISSION);
    validate(dto);
    // O mesmo nome (já sem espaços nas pontas) é checado e gravado; checar o original deixava
    // "X " passar quando "X" existia e estourava a unique do banco (500).
    String name = dto.name().trim();
    if (profileRepository.existsByName(name)) {
      throw alreadyExists(name);
    }
    return saveUnique(new Profile(name, dto.description()), name);
  }

  @Transactional
  public Profile updateProfile(long id, ProfileDto dto, long actingUserId) {
    authorizationService.requirePermission(actingUserId, ADMIN_PERMISSION);
    validate(dto);
    Profile profile = findById(id);
    String name = dto.name().trim();
    if (isAdminProfile(profile) && !name.equals(profile.name())) {
      throw new IllegalStateException("The administrator profile cannot be renamed");
    }
    if (profileRepository.existsByNameAndIdNot(name, id)) {
      throw alreadyExists(name);
    }
    profile.setName(name);
    profile.setDescription(dto.description());
    return saveUnique(profile, name);
  }

  @Transactional
  public void deleteProfile(long id, long actingUserId) {
    authorizationService.requirePermission(actingUserId, ADMIN_PERMISSION);
    Profile profile = findById(id);
    if (isAdminProfile(profile)) {
      throw new IllegalStateException("The administrator profile cannot be deleted");
    }
    // Qualquer usuário conta, inclusive a conta de sistema.
    if (userRepository.existsByProfile_Id(id)) {
      throw inUse();
    }
    try {
      profileRepository.deleteById(id);
      profileRepository.flush();
    } catch (DataIntegrityViolationException e) {
      // Um usuário recebeu o perfil entre a checagem acima e o delete.
      throw inUse();
    }
  }

  public Profile findProfileById(long id, long actingUserId) {
    authorizationService.requirePermission(actingUserId, ADMIN_PERMISSION);
    return findById(id);
  }

  public List<Profile> listAllProfiles(long actingUserId) {
    authorizationService.requirePermission(actingUserId, ADMIN_PERMISSION);
    return profileRepository.findAll();
  }

  @Transactional
  public Profile addPermission(long profileId, long permissionId, long actingUserId) {
    authorizationService.requirePermission(actingUserId, ADMIN_PERMISSION);
    Profile profile = findById(profileId);
    Permission permission = findPermission(permissionId);
    profile.addPermission(permission);
    return profileRepository.save(profile);
  }

  @Transactional
  public Profile removePermission(long profileId, long permissionId, long actingUserId) {
    authorizationService.requirePermission(actingUserId, ADMIN_PERMISSION);
    Profile profile = findById(profileId);
    Permission permission = findPermission(permissionId);
    if (isAdminProfile(profile) && ADMIN_PERMISSION.equals(permission.name())) {
      throw new IllegalStateException(
          "The administrator profile cannot lose the " + ADMIN_PERMISSION + " permission");
    }
    profile.removePermission(permission);
    return profileRepository.save(profile);
  }

  private Profile findById(long id) {
    return profileRepository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("Profile not found with id: " + id));
  }

  private Permission findPermission(long id) {
    return permissionRepository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("Permission not found with id: " + id));
  }

  private boolean isAdminProfile(Profile profile) {
    return Profile.ADMIN_PROFILE_NAME.equals(profile.name());
  }

  // Cobre a corrida de dois pedidos com o mesmo nome: a checagem de existência passa nos dois e a
  // unique do banco recusa o segundo.
  private Profile saveUnique(Profile profile, String name) {
    try {
      return profileRepository.saveAndFlush(profile);
    } catch (DataIntegrityViolationException e) {
      throw alreadyExists(name);
    }
  }

  private IllegalArgumentException alreadyExists(String name) {
    return new IllegalArgumentException("Profile with name '" + name + "' already exists");
  }

  private IllegalStateException inUse() {
    return new IllegalStateException("Profile is in use and cannot be deleted");
  }

  private void validate(ProfileDto dto) {
    if (dto.name() == null || dto.name().trim().isEmpty()) {
      throw new IllegalArgumentException("Profile name cannot be null or empty");
    }
    if (dto.name().trim().length() > 100) {
      throw new IllegalArgumentException("Profile name cannot exceed 100 characters");
    }
    if (dto.description() != null && dto.description().length() > 255) {
      throw new IllegalArgumentException("Profile description cannot exceed 255 characters");
    }
  }
}
