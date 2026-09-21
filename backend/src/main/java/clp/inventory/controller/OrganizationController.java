package clp.inventory.controller;

import clp.inventory.dto.OrganizationDto;
import clp.inventory.model.Organization;
import clp.inventory.service.OrganizationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * TODO: restringir estes endpoints à permissão ORGANIZATION_MANAGE (só administrador) quando
 * Profile/Permission forem implementados (ver docs/wiki/Proposta-de-Modelagem-Organizacoes-Setores-e-Permissoes.md).
 * Por enquanto, qualquer usuário autenticado pode gerenciar organizações, igual ao restante da API.
 */
@RestController
@RequestMapping("/organizations")
@CrossOrigin(origins = "*")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    public ResponseEntity<OrganizationDto> create(@RequestBody OrganizationDto dto) {
        try {
            Organization created = organizationService.createOrganization(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(OrganizationDto.from(created));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<OrganizationDto>> list() {
        List<OrganizationDto> dtos = organizationService.listAllOrganizations()
                .stream().map(OrganizationDto::from).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrganizationDto> getById(@PathVariable long id) {
        try {
            return ResponseEntity.ok(OrganizationDto.from(organizationService.findOrganizationById(id)));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrganizationDto> update(@PathVariable long id, @RequestBody OrganizationDto dto) {
        try {
            return ResponseEntity.ok(OrganizationDto.from(organizationService.updateOrganization(id, dto)));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        try {
            organizationService.deleteOrganization(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}
