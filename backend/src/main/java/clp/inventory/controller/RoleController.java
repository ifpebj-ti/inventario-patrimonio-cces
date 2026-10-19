package clp.inventory.controller;

import clp.inventory.dto.RoleDto;
import clp.inventory.dto.RoleRequest;
import clp.inventory.service.RoleService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Cargos. Ler a lista exige administrador ou gestor de algum setor (para escolher o cargo ao
 * alocar); criar, editar e excluir exigem administrador. Excluir cargo com alocação ou com
 * permissão responde 409.
 */
@RestController
@RequestMapping("/roles")
public class RoleController {
  private final RoleService roleService;

  public RoleController(RoleService roleService) {
    this.roleService = roleService;
  }

  @GetMapping
  public ResponseEntity<List<RoleDto>> list(HttpServletRequest request) {
    return ResponseEntity.ok(roleService.listRoles(actingUserId(request)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<RoleDto> getById(@PathVariable long id, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(roleService.findRole(id, actingUserId(request)));
    } catch (NoSuchElementException error) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, error.getMessage());
    }
  }

  @PostMapping
  public ResponseEntity<RoleDto> create(@RequestBody RoleRequest body, HttpServletRequest request) {
    try {
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(roleService.createRole(body, actingUserId(request)));
    } catch (IllegalArgumentException error) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage());
    }
  }

  @PutMapping("/{id}")
  public ResponseEntity<RoleDto> update(
      @PathVariable long id, @RequestBody RoleRequest body, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(roleService.updateRole(id, body, actingUserId(request)));
    } catch (NoSuchElementException error) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, error.getMessage());
    } catch (IllegalArgumentException error) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage());
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable long id, HttpServletRequest request) {
    try {
      roleService.deleteRole(id, actingUserId(request));
      return ResponseEntity.noContent().build();
    } catch (NoSuchElementException error) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, error.getMessage());
    }
  }

  private long actingUserId(HttpServletRequest request) {
    return Long.parseLong(request.getAttribute("id_user").toString());
  }
}
