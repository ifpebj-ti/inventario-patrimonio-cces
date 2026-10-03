package clp.inventory.controller;

import clp.inventory.dto.AssignProfileDto;
import clp.inventory.dto.AssignSectorDto;
import clp.inventory.model.User;
import clp.inventory.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * GET /users exige ADMIN. PATCH /users/{id}/sector exige ADMIN ou (MANAGE_SECTOR + estar alocado
 * no setor de destino) — ver AuthorizationService. TODO: restringir /users/{id}/profile quando a
 * checagem de permissão para atribuição de perfil for definida (ver
 * docs/wiki/Proposta-de-Modelagem-Organizacoes-Setores-e-Permissoes.md).
 */
@RestController
@CrossOrigin(origins = "*")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/users")
  public ResponseEntity<List<User>> list(
      @RequestParam(required = false) Long sectorId,
      @RequestParam(required = false) Long profileId,
      HttpServletRequest request) {
    long actingUserId = Long.parseLong(request.getAttribute("id_user").toString());
    return ResponseEntity.ok(userService.listUsers(sectorId, profileId, actingUserId));
  }

  @PatchMapping("/users/{id}/sector")
  public ResponseEntity<User> assignSector(
      @PathVariable long id, @RequestBody AssignSectorDto dto, HttpServletRequest request) {
    try {
      long actingUserId = Long.parseLong(request.getAttribute("id_user").toString());
      return ResponseEntity.ok(userService.assignSector(id, dto.sectorId(), actingUserId));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  @PatchMapping("/users/{id}/profile")
  public ResponseEntity<User> assignProfile(
      @PathVariable long id, @RequestBody AssignProfileDto dto) {
    try {
      return ResponseEntity.ok(userService.assignProfile(id, dto.profileId()));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }
}
