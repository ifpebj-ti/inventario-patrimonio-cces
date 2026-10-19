package clp.inventory.controller;

import clp.inventory.dto.AllocateSectorDto;
import clp.inventory.dto.SetAdministratorDto;
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
 * GET /users exige administrador ou gestor (MANAGE_SECTOR) de algum setor. PUT/DELETE
 * /users/{id}/sectors/{sectorId} exige administrador ou gestor daquele setor — o PUT cria a
 * alocação ou troca o cargo do usuário no setor (ver AuthorizationService). PATCH
 * /users/{id}/activate, /deactivate e /administrator exigem administrador.
 *
 * <p>Não há criar nem excluir usuário pela API: o usuário nasce no primeiro login pelo Google (sem
 * alocação e sem ser administrador) e nunca é apagado, só desativado.
 */
@RestController
public class UserController {

  private final UserService userService;
  private final SectorAllocationService sectorAllocationService;

  public UserController(UserService userService, SectorAllocationService sectorAllocationService) {
    this.userService = userService;
    this.sectorAllocationService = sectorAllocationService;
  }

  @GetMapping("/users")
  public ResponseEntity<List<UserDto>> list(
      @RequestParam(required = false) Long roleId, HttpServletRequest request) {
    long actingUserId = actingUserId(request);
    List<UserDto> dtos =
        userService.listUsers(roleId, actingUserId).stream().map(this::toDto).toList();
    return ResponseEntity.ok(dtos);
  }

  @PutMapping("/users/{id}/sectors/{sectorId}")
  public ResponseEntity<UserDto> allocateSector(
      @PathVariable long id,
      @PathVariable long sectorId,
      @RequestBody AllocateSectorDto dto,
      HttpServletRequest request) {
    try {
      long actingUserId = actingUserId(request);
      User user = sectorAllocationService.allocate(id, sectorId, dto.roleId(), actingUserId);
      return ResponseEntity.ok(toDto(user));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
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

  @PatchMapping("/users/{id}/administrator")
  public ResponseEntity<UserDto> setAdministrator(
      @PathVariable long id, @RequestBody SetAdministratorDto dto, HttpServletRequest request) {
    if (dto.administrator() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o campo administrator.");
    }
    try {
      return ResponseEntity.ok(
          toDto(userService.setAdministrator(id, dto.administrator(), actingUserId(request))));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @PatchMapping("/users/{id}/deactivate")
  public ResponseEntity<UserDto> deactivate(@PathVariable long id, HttpServletRequest request) {
    return setActive(id, false, request);
  }

  @PatchMapping("/users/{id}/activate")
  public ResponseEntity<UserDto> activate(@PathVariable long id, HttpServletRequest request) {
    return setActive(id, true, request);
  }

  private ResponseEntity<UserDto> setActive(long id, boolean active, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(toDto(userService.setActive(id, active, actingUserId(request))));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  private UserDto toDto(User user) {
    return UserDto.from(user, sectorAllocationService.allocationsOf(user.getId()));
  }

  private long actingUserId(HttpServletRequest request) {
    return Long.parseLong(request.getAttribute("id_user").toString());
  }
}
