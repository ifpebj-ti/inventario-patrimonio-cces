package clp.inventory.controller;

import clp.inventory.dto.SectorDto;
import clp.inventory.model.Sector;
import clp.inventory.service.SectorService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Criar, editar, ativar e desativar setor exige a permissão ADMIN; listar e buscar por id exige
 * ADMIN ou MANAGE_SECTOR (ver AuthorizationService e
 * docs/wiki/Proposta-de-Modelagem-Organizacoes-Setores-e-Permissoes.md).
 */
@RestController
@RequestMapping("/sectors")
@CrossOrigin(origins = "*")
public class SectorController {

  private final SectorService sectorService;

  public SectorController(SectorService sectorService) {
    this.sectorService = sectorService;
  }

  @PostMapping
  public ResponseEntity<SectorDto> create(@RequestBody SectorDto dto, HttpServletRequest request) {
    try {
      Sector created = sectorService.createSector(dto, actingUserId(request));
      return ResponseEntity.status(HttpStatus.CREATED).body(SectorDto.from(created));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  @GetMapping
  public ResponseEntity<List<SectorDto>> list(HttpServletRequest request) {
    List<SectorDto> dtos =
        sectorService.listAllSectors(actingUserId(request)).stream()
            .map(SectorDto::from)
            .toList();
    return ResponseEntity.ok(dtos);
  }

  @GetMapping("/{id}")
  public ResponseEntity<SectorDto> getById(@PathVariable long id, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(
          SectorDto.from(sectorService.findSectorById(id, actingUserId(request))));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @PutMapping("/{id}")
  public ResponseEntity<SectorDto> update(
      @PathVariable long id, @RequestBody SectorDto dto, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(
          SectorDto.from(sectorService.updateSector(id, dto, actingUserId(request))));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  @PatchMapping("/{id}/activate")
  public ResponseEntity<SectorDto> activate(@PathVariable long id, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(
          SectorDto.from(sectorService.activateSector(id, actingUserId(request))));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @PatchMapping("/{id}/deactivate")
  public ResponseEntity<SectorDto> deactivate(@PathVariable long id, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(
          SectorDto.from(sectorService.deactivateSector(id, actingUserId(request))));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  private long actingUserId(HttpServletRequest request) {
    return Long.parseLong(request.getAttribute("id_user").toString());
  }
}
