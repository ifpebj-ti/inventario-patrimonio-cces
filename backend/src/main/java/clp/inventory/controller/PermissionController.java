package clp.inventory.controller;

import clp.inventory.dto.PermissionDto;
import clp.inventory.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Permissões", description = "Permissões do sistema (somente leitura).")
@RestController
@RequestMapping("/permissions")
public class PermissionController {

  private final PermissionService permissionService;

  public PermissionController(PermissionService permissionService) {
    this.permissionService = permissionService;
  }

  @Operation(
      summary = "Listar permissões",
      description = "Exige administrador ou gestor (MANAGE_SECTOR) de algum setor.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Permissões.",
        content =
            @Content(array = @ArraySchema(schema = @Schema(implementation = PermissionDto.class))))
  })
  @GetMapping
  public ResponseEntity<List<PermissionDto>> list(HttpServletRequest request) {
    List<PermissionDto> dtos =
        permissionService.listAllPermissions(actingUserId(request)).stream()
            .map(PermissionDto::from)
            .toList();
    return ResponseEntity.ok(dtos);
  }

  @Operation(summary = "Buscar permissão por id", description = "Exige administrador.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Permissão.",
        content = @Content(schema = @Schema(implementation = PermissionDto.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Permissão não encontrada.",
        content = @Content)
  })
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
