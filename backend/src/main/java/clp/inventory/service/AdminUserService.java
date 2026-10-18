package clp.inventory.service;

import clp.inventory.dto.CreateUserDto;
import clp.inventory.model.*;
import clp.inventory.repository.*;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUserService {
  private final AuthorizationService authorization;
  private final UserRepository users;
  private final ProfileRepository profiles;
  private final SectorRepository sectors;
  private final SectorAllocationRepository allocations;

  public AdminUserService(
      AuthorizationService authorization,
      UserRepository users,
      ProfileRepository profiles,
      SectorRepository sectors,
      SectorAllocationRepository allocations) {
    this.authorization = authorization;
    this.users = users;
    this.profiles = profiles;
    this.sectors = sectors;
    this.allocations = allocations;
  }

  @Transactional
  public User create(CreateUserDto dto, long actingUserId) {
    authorization.requireAnyPermission(
        actingUserId, AuthorizationService.ADMIN, AuthorizationService.MANAGE_SECTOR);
    String email = dto.email().trim().toLowerCase(Locale.ROOT);
    if (users.findByEmailIgnoreCase(email).isPresent()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Já existe um usuário com esse e-mail.");
    }
    if (dto.profileId() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione o perfil de acesso.");
    }
    Profile profile =
        profiles
            .findById(dto.profileId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.BAD_REQUEST, "Perfil não encontrado."));
    // Gestor cadastra apenas Operadores de campo e Consulta, e só nos seus próprios setores.
    authorization.requireAssignProfile(actingUserId, profile);
    var sectorIds = dto.sectorIds().stream().distinct().toList();
    if (!authorization.isAdmin(actingUserId)) {
      if (sectorIds.isEmpty()) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Informe ao menos um setor para o novo usuário.");
      }
      if (!authorization.allocatedSectorIds(actingUserId).containsAll(sectorIds)) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, "Você só pode vincular usuários aos setores em que atua.");
      }
    }
    var selectedSectors = sectors.findAllById(sectorIds);
    if (selectedSectors.size() != sectorIds.size()
        || selectedSectors.stream().anyMatch(s -> !s.active())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Selecione somente setores ativos existentes.");
    }
    User user = new User();
    user.setEmail(email);
    user.setName(
        dto.name() == null || dto.name().isBlank() ? email.split("@")[0] : dto.name().trim());
    user.setProfile(profile);
    user.setPendingGoogleLogin(true);
    User saved = users.saveAndFlush(user);
    allocations.saveAll(selectedSectors.stream().map(s -> new SectorAllocation(saved, s)).toList());
    return saved;
  }
}
