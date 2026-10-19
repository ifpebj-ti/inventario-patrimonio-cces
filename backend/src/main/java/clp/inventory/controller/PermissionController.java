package clp.inventory.controller;

import clp.inventory.dto.PermissionDto;
import clp.inventory.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Somente leitura; a lista exige administrador ou gestor de algum setor e a consulta por id exige
 * administrador (403 caso contrário). Não há criar/editar/excluir: permissões são definidas em
 * código e uma nova entra por migration do Liquibase.
 */
@RestController
@RequestMapping("/permissions")
public class PermissionController {

  private final PermissionService permissionService;

  public PermissionController(PermissionService permissionService) {
    this.permissionService = permissionService;
  }

  @GetMapping
  public ResponseEntity<List<PermissionDto>> list(HttpServletRequest request) {
    List<PermissionDto> dtos =
        permissionService.listAllPermissions(actingUserId(request)).stream()
            .map(PermissionDto::from)
            .toList();
    return ResponseEntity.ok(dtos);
  }

  @GetMapping("/{id}")
  public ResponseEntity<PermissionDto> getById(@PathVariable long id, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(
          PermissionDto.from(permissionService.findPermissionById(id, actingUserId(request))));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  private long actingUserId(HttpServletRequest request) {
    return Long.parseLong(request.getAttribute("id_user").toString());
  }
}
