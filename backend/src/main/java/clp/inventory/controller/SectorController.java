package clp.inventory.controller;

import clp.inventory.dto.SectorDto;
import clp.inventory.dto.UserDto;
import clp.inventory.model.Sector;
import clp.inventory.service.SectorAllocationService;
import clp.inventory.service.SectorService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Criar, editar, ativar, desativar e excluir setor exige a permissão ADMIN (a exclusão só vale para
 * setor sem usuário, inventário nem auditoria; senão 409); listar e buscar por id exige ADMIN ou
 * MANAGE_SECTOR com vínculo ao setor; gestores só listam seus setores (ver AuthorizationService e
 * docs/wiki/Proposta-de-Modelagem-Organizacoes-Setores-e-Permissoes.md).
 */
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

  @PostMapping
  public ResponseEntity<SectorDto> create(@RequestBody SectorDto dto, HttpServletRequest request) {
    try {
      Sector created = sectorService.createSector(dto, actingUserId(request));
      return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  @GetMapping
  public ResponseEntity<List<SectorDto>> list(HttpServletRequest request) {
    List<SectorDto> dtos =
        sectorService.listAllSectors(actingUserId(request)).stream().map(this::toDto).toList();
    return ResponseEntity.ok(dtos);
  }

  @GetMapping("/{id}")
  public ResponseEntity<SectorDto> getById(@PathVariable long id, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(toDto(sectorService.findSectorById(id, actingUserId(request))));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

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

  @PatchMapping("/{id}/activate")
  public ResponseEntity<SectorDto> activate(@PathVariable long id, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(toDto(sectorService.activateSector(id, actingUserId(request))));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @PatchMapping("/{id}/deactivate")
  public ResponseEntity<SectorDto> deactivate(@PathVariable long id, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(toDto(sectorService.deactivateSector(id, actingUserId(request))));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable long id, HttpServletRequest request) {
    try {
      sectorService.deleteSector(id, actingUserId(request));
      return ResponseEntity.noContent().build();
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @GetMapping("/{id}/users")
  public ResponseEntity<List<UserDto>> listUsers(
      @PathVariable long id, HttpServletRequest request) {
    try {
      long actingUserId = actingUserId(request);
      sectorService.findSectorForManagement(id, actingUserId);
      List<UserDto> dtos =
          sectorAllocationService.usersAllocatedToSector(id).stream()
              .map(u -> UserDto.from(u, sectorAllocationService.sectorIdsOf(u.getId())))
              .toList();
      return ResponseEntity.ok(dtos);
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  private SectorDto toDto(Sector sector) {
    return SectorDto.from(sector, sectorService.isDeletable(sector.id()));
  }

  private long actingUserId(HttpServletRequest request) {
    return Long.parseLong(request.getAttribute("id_user").toString());
  }
}
