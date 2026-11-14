package clp.inventory.controller;

import clp.inventory.dto.RoleDto;
import clp.inventory.dto.RoleRequest;
import clp.inventory.service.RoleService;
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
 * Cargos. Ler a lista exige administrador ou gestor de algum setor (para escolher o cargo ao
 * alocar); criar, editar e excluir exigem administrador. Excluir cargo com alocação ou com
 * permissão responde 409.
 */
@Tag(name = "Cargos", description = "Cargos e as permissões que carregam.")
@RestController
@RequestMapping("/roles")
public class RoleController {
  private final RoleService roleService;

  public RoleController(RoleService roleService) {
    this.roleService = roleService;
  }

  @Operation(
      summary = "Listar cargos",
      description =
          "Exige administrador ou gestor de algum setor (para escolher o cargo ao alocar).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Cargos.",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = RoleDto.class))))
  })
  @GetMapping
  public ResponseEntity<List<RoleDto>> list(HttpServletRequest request) {
    return ResponseEntity.ok(roleService.listRoles(actingUserId(request)));
  }

  @Operation(summary = "Buscar cargo por id", description = "")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Cargo.",
        content = @Content(schema = @Schema(implementation = RoleDto.class))),
    @ApiResponse(responseCode = "404", description = "Cargo não encontrado.", content = @Content)
  })
  @GetMapping("/{id}")
  public ResponseEntity<RoleDto> getById(@PathVariable long id, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(roleService.findRole(id, actingUserId(request)));
    } catch (NoSuchElementException error) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, error.getMessage());
    }
  }

  @Operation(summary = "Criar cargo", description = "Exige administrador.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Cargo criado.",
        content = @Content(schema = @Schema(implementation = RoleDto.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Dados inválidos (ex.: nome vazio ou permissão inexistente).",
        content = @Content)
  })
  @PostMapping
  public ResponseEntity<RoleDto> create(@RequestBody RoleRequest body, HttpServletRequest request) {
    try {
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(roleService.createRole(body, actingUserId(request)));
    } catch (IllegalArgumentException error) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage());
    }
  }

  @Operation(summary = "Editar cargo", description = "Exige administrador.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Cargo atualizado.",
        content = @Content(schema = @Schema(implementation = RoleDto.class))),
    @ApiResponse(responseCode = "400", description = "Dados inválidos.", content = @Content),
    @ApiResponse(responseCode = "404", description = "Cargo não encontrado.", content = @Content)
  })
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

  @Operation(
      summary = "Excluir cargo",
      description =
          "Exige administrador. Só cargo sem alocação e sem permissão pode ser excluído; senão responde 409.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Cargo excluído.", content = @Content),
    @ApiResponse(responseCode = "404", description = "Cargo não encontrado.", content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "O cargo tem alocação ou permissão.",
        content = @Content)
  })
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
