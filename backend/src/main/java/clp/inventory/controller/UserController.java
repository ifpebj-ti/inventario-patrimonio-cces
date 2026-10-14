package clp.inventory.controller;

import clp.inventory.dto.AssignProfileDto;
import clp.inventory.dto.CreateUserDto;
import clp.inventory.dto.UserDto;
import clp.inventory.model.User;
import clp.inventory.service.AdminUserService;
import clp.inventory.service.SectorAllocationService;
import clp.inventory.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * GET/POST /users e PATCH /users/{id}/profile exigem ADMIN. POST/DELETE
 * /users/{id}/sectors/{sectorId} exige ADMIN ou (MANAGE_SECTOR + estar alocado no setor informado)
 * — ver AuthorizationService.
 */
@RestController
public class UserController {

  private final UserService userService;
  private final SectorAllocationService sectorAllocationService;
  private final AdminUserService adminUserService;

  public UserController(
      UserService userService,
      SectorAllocationService sectorAllocationService,
      AdminUserService adminUserService) {
    this.userService = userService;
    this.sectorAllocationService = sectorAllocationService;
    this.adminUserService = adminUserService;
  }

  @PostMapping("/users")
  public ResponseEntity<UserDto> create(
      @Valid @RequestBody CreateUserDto dto, HttpServletRequest request) {
    try {
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(toDto(adminUserService.create(dto, actingUserId(request))));
    } catch (DataIntegrityViolationException error) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Não foi possível cadastrar. Atualize os dados e confira se o e-mail já existe.");
    }
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
      @PathVariable long id, @RequestBody AssignProfileDto dto, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(
          toDto(userService.assignProfile(id, dto.profileId(), actingUserId(request))));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
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

  @DeleteMapping("/users/{id}")
  public ResponseEntity<Void> delete(@PathVariable long id, HttpServletRequest request) {
    try {
      userService.deleteUser(id, actingUserId(request));
      return ResponseEntity.noContent().build();
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  private ResponseEntity<UserDto> setActive(long id, boolean active, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(toDto(userService.setActive(id, active, actingUserId(request))));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  private UserDto toDto(User user) {
    return UserDto.from(user, sectorAllocationService.sectorIdsOf(user.getId()));
  }

  private long actingUserId(HttpServletRequest request) {
    return Long.parseLong(request.getAttribute("id_user").toString());
  }
}
