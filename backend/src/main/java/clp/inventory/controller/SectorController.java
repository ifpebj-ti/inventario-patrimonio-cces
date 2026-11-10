package clp.inventory.controller;

import clp.inventory.dto.AllocateMembersDto;
import clp.inventory.dto.SectorDto;
import clp.inventory.dto.UserDto;
import clp.inventory.dto.UserSummaryDto;
import clp.inventory.model.Sector;
import clp.inventory.service.SectorAllocationService;
import clp.inventory.service.SectorService;
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
 * Criar, editar, ativar, desativar e excluir setor exige ser administrador (a exclusão só vale para
 * setor sem usuário, inventário nem auditoria; senão 409). Listar e buscar por id vale para o
 * administrador e para quem está alocado ao setor; gestores só listam os seus setores. Ver a lista
 * de membros e alocar usuários (GET /{id}/users, GET /{id}/assignable-users e PUT /{id}/members)
 * exige administrador ou gestor (MANAGE_SECTOR) daquele setor. Ver AuthorizationService.
 */
@Tag(name = "Setores", description = "Setores, membros e alocação em lote.")
@RestController
@RequestMapping("/sectors")
public class SectorController {

  private final SectorService sectorService;
  private final SectorAllocationService sectorAllocationService;

  public SectorController(
      SectorService sectorService, SectorAllocationService sectorAllocationService) {
    this.sectorService = sectorService;
    this.sectorAllocationService = sectorAllocationService;
  }

  @Operation(summary = "Criar setor", description = "Exige administrador.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Setor criado.",
        content = @Content(schema = @Schema(implementation = SectorDto.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Nome vazio ou já existente, ou nome/código longos demais.",
        content = @Content)
  })
  @PostMapping
  public ResponseEntity<SectorDto> create(@RequestBody SectorDto dto, HttpServletRequest request) {
    try {
      Sector created = sectorService.createSector(dto, actingUserId(request));
      return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  @Operation(
      summary = "Listar setores",
      description = "O administrador vê todos; os demais, só os setores a que estão alocados.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Setores.",
        content =
            @Content(array = @ArraySchema(schema = @Schema(implementation = SectorDto.class))))
  })
  @GetMapping
  public ResponseEntity<List<SectorDto>> list(HttpServletRequest request) {
    List<SectorDto> dtos =
        sectorService.listAllSectors(actingUserId(request)).stream().map(this::toDto).toList();
    return ResponseEntity.ok(dtos);
  }

  @Operation(
      summary = "Buscar setor por id",
      description = "Vale para o administrador e para quem está alocado ao setor.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Setor.",
        content = @Content(schema = @Schema(implementation = SectorDto.class))),
    @ApiResponse(responseCode = "404", description = "Setor não encontrado.", content = @Content)
  })
  @GetMapping("/{id}")
  public ResponseEntity<SectorDto> getById(@PathVariable long id, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(toDto(sectorService.findSectorById(id, actingUserId(request))));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @Operation(summary = "Editar setor", description = "Exige administrador.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Setor atualizado.",
        content = @Content(schema = @Schema(implementation = SectorDto.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Nome vazio ou já existente, ou nome/código longos demais.",
        content = @Content),
    @ApiResponse(responseCode = "404", description = "Setor não encontrado.", content = @Content)
  })
  @PutMapping("/{id}")
  public ResponseEntity<SectorDto> update(
      @PathVariable long id, @RequestBody SectorDto dto, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(toDto(sectorService.updateSector(id, dto, actingUserId(request))));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  @Operation(summary = "Ativar setor", description = "Exige administrador.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Setor ativado.",
        content = @Content(schema = @Schema(implementation = SectorDto.class))),
    @ApiResponse(responseCode = "404", description = "Setor não encontrado.", content = @Content)
  })
  @PatchMapping("/{id}/activate")
  public ResponseEntity<SectorDto> activate(@PathVariable long id, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(toDto(sectorService.activateSector(id, actingUserId(request))));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @Operation(summary = "Desativar setor", description = "Exige administrador.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Setor desativado.",
        content = @Content(schema = @Schema(implementation = SectorDto.class))),
    @ApiResponse(responseCode = "404", description = "Setor não encontrado.", content = @Content)
  })
  @PatchMapping("/{id}/deactivate")
  public ResponseEntity<SectorDto> deactivate(@PathVariable long id, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(toDto(sectorService.deactivateSector(id, actingUserId(request))));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @Operation(
      summary = "Excluir setor",
      description =
          "Exige administrador. Só vale para setor sem usuário, inventário nem auditoria (veja deletable em SectorDto); senão responde 409.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Setor excluído.", content = @Content),
    @ApiResponse(responseCode = "404", description = "Setor não encontrado.", content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "O setor tem usuários ou inventários associados.",
        content = @Content)
  })
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable long id, HttpServletRequest request) {
    try {
      sectorService.deleteSector(id, actingUserId(request));
      return ResponseEntity.noContent().build();
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @Operation(
      summary = "Listar membros do setor",
      description = "Exige administrador ou gestor (MANAGE_SECTOR) do setor.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Usuários alocados ao setor.",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserDto.class)))),
    @ApiResponse(responseCode = "404", description = "Setor não encontrado.", content = @Content)
  })
  @GetMapping("/{id}/users")
  public ResponseEntity<List<UserDto>> listUsers(
      @PathVariable long id, HttpServletRequest request) {
    try {
      long actingUserId = actingUserId(request);
      sectorService.findSectorForManagement(id, actingUserId);
      List<UserDto> dtos =
          sectorAllocationService.usersAllocatedToSector(id).stream()
              .map(u -> UserDto.from(u, sectorAllocationService.allocationsOf(u.getId())))
              .toList();
      return ResponseEntity.ok(dtos);
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @Operation(
      summary = "Listar usuários alocáveis ao setor",
      description =
          "Usuários ativos que ainda não estão no setor, só com nome e e-mail. Exige administrador ou gestor (MANAGE_SECTOR) do setor.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Usuários que podem ser alocados.",
        content =
            @Content(
                array = @ArraySchema(schema = @Schema(implementation = UserSummaryDto.class)))),
    @ApiResponse(responseCode = "404", description = "Setor não encontrado.", content = @Content)
  })
  /** Quem ainda pode ser alocado ao setor (ativos que não estão nele): nome e e-mail, só. */
  @GetMapping("/{id}/assignable-users")
  public ResponseEntity<List<UserSummaryDto>> assignableUsers(
      @PathVariable long id, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(
          sectorAllocationService.assignableUsers(id, actingUserId(request)).stream()
              .map(UserSummaryDto::from)
              .toList());
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  /** Aloca vários usuários com o mesmo cargo, tudo ou nada; devolve os membros do setor. */
  @Operation(
      summary = "Alocar vários usuários ao setor",
      description =
          "Aloca todos os usuários com o mesmo cargo, tudo ou nada, e devolve os membros do setor. Exige administrador ou gestor (MANAGE_SECTOR) do setor; o gestor só aloca usuários com cargos sem permissão de gestão.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Membros do setor depois da alocação.",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserDto.class)))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Nenhum usuário (ou mais de 200), lista com itens vazios ou repetidos, cargo ausente ou inexistente.",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "Setor ou usuário não encontrado.",
        content = @Content)
  })
  @PutMapping("/{id}/members")
  public ResponseEntity<List<UserDto>> allocateMembers(
      @PathVariable long id, @RequestBody AllocateMembersDto dto, HttpServletRequest request) {
    try {
      List<UserDto> members =
          sectorAllocationService
              .allocateMany(id, dto.userIds(), dto.roleId(), actingUserId(request))
              .stream()
              .map(u -> UserDto.from(u, sectorAllocationService.allocationsOf(u.getId())))
              .toList();
      return ResponseEntity.ok(members);
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  private SectorDto toDto(Sector sector) {
    return SectorDto.from(sector, sectorService.isDeletable(sector.id()));
  }

  private long actingUserId(HttpServletRequest request) {
    return Long.parseLong(request.getAttribute("id_user").toString());
  }
}
