package clp.inventory.controller;

import clp.inventory.dto.SectorDto;
import clp.inventory.model.Sector;
import clp.inventory.service.SectorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * TODO: restringir estes endpoints à permissão SECTOR_MANAGE (só administrador/gestor) quando
 * Profile/Permission forem implementados (ver docs/wiki/Proposta-de-Modelagem-Organizacoes-Setores-e-Permissoes.md).
 * Por enquanto, qualquer usuário autenticado pode gerenciar setores, igual ao restante da API.
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
    public ResponseEntity<SectorDto> create(@RequestBody SectorDto dto) {
        try {
            Sector created = sectorService.createSector(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(SectorDto.from(created));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<SectorDto>> list(@RequestParam(required = false) Long organizationId) {
        List<SectorDto> dtos = sectorService.listSectors(organizationId)
                .stream().map(SectorDto::from).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SectorDto> getById(@PathVariable long id) {
        try {
            return ResponseEntity.ok(SectorDto.from(sectorService.findSectorById(id)));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<SectorDto> update(@PathVariable long id, @RequestBody SectorDto dto) {
        try {
            return ResponseEntity.ok(SectorDto.from(sectorService.updateSector(id, dto)));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        try {
            sectorService.deleteSector(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}
