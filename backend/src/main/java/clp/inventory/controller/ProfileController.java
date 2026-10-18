package clp.inventory.controller;

import clp.inventory.dto.ProfileDto;
import clp.inventory.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Catálogo somente para leitura. A associação de um perfil ao usuário permanece em /users. */
@RestController
@RequestMapping("/profiles")
public class ProfileController {
  private final ProfileService profileService;

  public ProfileController(ProfileService profileService) {
    this.profileService = profileService;
  }

  @GetMapping
  public ResponseEntity<List<ProfileDto>> list(HttpServletRequest request) {
    return ResponseEntity.ok(
        profileService.listAllProfiles(actingUserId(request)).stream()
            .map(ProfileDto::from)
            .toList());
  }

  @GetMapping("/{id}")
  public ResponseEntity<ProfileDto> getById(@PathVariable long id, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(
          ProfileDto.from(profileService.findProfileById(id, actingUserId(request))));
    } catch (NoSuchElementException error) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, error.getMessage());
    }
  }

  @RequestMapping(
      value = {"", "/{id}", "/{id}/permissions/{permissionId}"},
      method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
  public ResponseEntity<Void> rejectChanges(HttpServletRequest request) {
    profileService.rejectChanges(actingUserId(request));
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
  }

  private long actingUserId(HttpServletRequest request) {
    return Long.parseLong(request.getAttribute("id_user").toString());
  }
}
