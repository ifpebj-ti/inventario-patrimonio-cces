package clp.inventory.controller;

import clp.inventory.dto.ProfileDto;
import clp.inventory.model.Profile;
import clp.inventory.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Todas as rotas exigem a permissão ADMIN (403 caso contrário). 409 quando a operação esbarra no
 * perfil de administrador (protegido) ou em um perfil que ainda tem usuários.
 */
@RestController
@RequestMapping("/profiles")
public class ProfileController {

  private final ProfileService profileService;

  public ProfileController(ProfileService profileService) {
    this.profileService = profileService;
  }

  @PostMapping
  public ResponseEntity<ProfileDto> create(
      @RequestBody ProfileDto dto, HttpServletRequest request) {
    try {
      Profile created = profileService.createProfile(dto, actingUserId(request));
      return ResponseEntity.status(HttpStatus.CREATED).body(ProfileDto.from(created));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  @GetMapping
  public ResponseEntity<List<ProfileDto>> list(HttpServletRequest request) {
    List<ProfileDto> dtos =
        profileService.listAllProfiles(actingUserId(request)).stream()
            .map(ProfileDto::from)
            .toList();
    return ResponseEntity.ok(dtos);
  }

  @GetMapping("/{id}")
  public ResponseEntity<ProfileDto> getById(@PathVariable long id, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(
          ProfileDto.from(profileService.findProfileById(id, actingUserId(request))));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @PutMapping("/{id}")
  public ResponseEntity<ProfileDto> update(
      @PathVariable long id, @RequestBody ProfileDto dto, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(
          ProfileDto.from(profileService.updateProfile(id, dto, actingUserId(request))));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable long id, HttpServletRequest request) {
    try {
      profileService.deleteProfile(id, actingUserId(request));
      return ResponseEntity.noContent().build();
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
    }
  }

  @PostMapping("/{id}/permissions/{permissionId}")
  public ResponseEntity<ProfileDto> addPermission(
      @PathVariable long id, @PathVariable long permissionId, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(
          ProfileDto.from(profileService.addPermission(id, permissionId, actingUserId(request))));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @DeleteMapping("/{id}/permissions/{permissionId}")
  public ResponseEntity<ProfileDto> removePermission(
      @PathVariable long id, @PathVariable long permissionId, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(
          ProfileDto.from(
              profileService.removePermission(id, permissionId, actingUserId(request))));
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
    }
  }

  private long actingUserId(HttpServletRequest request) {
    return Long.parseLong(request.getAttribute("id_user").toString());
  }
}
