package clp.inventory.controller;

import clp.inventory.dto.ProfileDto;
import clp.inventory.model.Profile;
import clp.inventory.service.ProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * TODO: restringir estes endpoints a uma permissão administrativa quando Profile/Permission
 * estiverem totalmente implementados (ver docs/wiki/Proposta-de-Modelagem-Organizacoes-Setores-e-Permissoes.md).
 * Por enquanto, qualquer usuário autenticado pode gerenciar perfis, igual ao restante da API.
 */
@RestController
@RequestMapping("/profiles")
@CrossOrigin(origins = "*")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PostMapping
    public ResponseEntity<ProfileDto> create(@RequestBody ProfileDto dto) {
        try {
            Profile created = profileService.createProfile(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(ProfileDto.from(created));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<ProfileDto>> list() {
        List<ProfileDto> dtos = profileService.listAllProfiles()
                .stream().map(ProfileDto::from).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProfileDto> getById(@PathVariable long id) {
        try {
            return ResponseEntity.ok(ProfileDto.from(profileService.findProfileById(id)));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProfileDto> update(@PathVariable long id, @RequestBody ProfileDto dto) {
        try {
            return ResponseEntity.ok(ProfileDto.from(profileService.updateProfile(id, dto)));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        try {
            profileService.deleteProfile(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}
