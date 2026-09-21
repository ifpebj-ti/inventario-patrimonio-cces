package clp.inventory.controller;

import clp.inventory.dto.PermissionDto;
import clp.inventory.model.Permission;
import clp.inventory.service.PermissionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * TODO: restringir estes endpoints a uma permissão administrativa quando Profile/Permission
 * estiverem totalmente implementados (ver docs/wiki/Proposta-de-Modelagem-Organizacoes-Setores-e-Permissoes.md).
 * Por enquanto, qualquer usuário autenticado pode gerenciar permissões, igual ao restante da API.
 */
@RestController
@RequestMapping("/permissions")
@CrossOrigin(origins = "*")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @PostMapping
    public ResponseEntity<PermissionDto> create(@RequestBody PermissionDto dto) {
        try {
            Permission created = permissionService.createPermission(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(PermissionDto.from(created));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<PermissionDto>> list() {
        List<PermissionDto> dtos = permissionService.listAllPermissions()
                .stream().map(PermissionDto::from).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PermissionDto> getById(@PathVariable long id) {
        try {
            return ResponseEntity.ok(PermissionDto.from(permissionService.findPermissionById(id)));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<PermissionDto> update(@PathVariable long id, @RequestBody PermissionDto dto) {
        try {
            return ResponseEntity.ok(PermissionDto.from(permissionService.updatePermission(id, dto)));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        try {
            permissionService.deletePermission(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}
