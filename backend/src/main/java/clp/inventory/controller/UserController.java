package clp.inventory.controller;

import clp.inventory.dto.AssignProfileDto;
import clp.inventory.dto.UserDto;
import clp.inventory.model.User;
import clp.inventory.service.SectorAllocationService;
import clp.inventory.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * GET /users exige ADMIN. POST/DELETE /users/{id}/sectors/{sectorId} exige ADMIN ou
 * (MANAGE_SECTOR + estar alocado no setor informado) — ver AuthorizationService. TODO: restringir
 * /users/{id}/profile quando a checagem de permissão para atribuição de perfil for definida (ver
 * docs/wiki/Proposta-de-Modelagem-Organizacoes-Setores-e-Permissoes.md).
 */
@RestController
@CrossOrigin(origins = "*")
public class UserController {

  private final UserService userService;
  private final SectorAllocationService sectorAllocationService;

  public UserController(UserService userService, SectorAllocationService sectorAllocationService) {
    this.userService = userService;
    this.sectorAllocationService = sectorAllocationService;
  }

  @GetMapping("/users")
  public ResponseEntity<List<UserDto>> list(
      @RequestParam(required = false) Long profileId, HttpServletRequest request) {
    long actingUserId = actingUserId(request);
    List<UserDto> dtos =
        userService.listUsers(profileId, actingUserId).stream().map(this::toDto).toList();
    return ResponseEntity.ok(dtos);
  }

  @PostMapping("/users/{id}/sectors/{sectorId}")
  public ResponseEntity<UserDto> allocateSector(
      @PathVariable long id, @PathVariable long sectorId, HttpServletRequest request) {
    try {
      long actingUserId = actingUserId(request);
      User user = sectorAllocationService.allocate(id, sectorId, actingUserId);
      return ResponseEntity.ok(toDto(user));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @DeleteMapping("/users/{id}/sectors/{sectorId}")
  public ResponseEntity<UserDto> deallocateSector(
      @PathVariable long id, @PathVariable long sectorId, HttpServletRequest request) {
    try {
      long actingUserId = actingUserId(request);
      User user = sectorAllocationService.deallocate(id, sectorId, actingUserId);
      return ResponseEntity.ok(toDto(user));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @PatchMapping("/users/{id}/profile")
  public ResponseEntity<UserDto> assignProfile(
      @PathVariable long id, @RequestBody AssignProfileDto dto) {
    try {
      return ResponseEntity.ok(toDto(userService.assignProfile(id, dto.profileId())));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  private UserDto toDto(User user) {
    return UserDto.from(user, sectorAllocationService.sectorIdsOf(user.getId()));
  }

  private long actingUserId(HttpServletRequest request) {
    return Long.parseLong(request.getAttribute("id_user").toString());
  }
}
