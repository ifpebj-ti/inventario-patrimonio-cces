package clp.inventory.controller;

import clp.inventory.dto.AssignProfileDto;
import clp.inventory.dto.AssignSectorDto;
import clp.inventory.model.User;
import clp.inventory.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * TODO: restringir /users/{id}/sector e /users/{id}/profile às permissões USER_ASSIGN_SECTOR e
 * USER_ASSIGN_PROFILE (já existem, seedadas em #155) quando a checagem de permissão por perfil
 * for implementada.
 */
@RestController
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> list(
            @RequestParam(required = false) Long sectorId,
            @RequestParam(required = false) Long profileId
    ) {
        return ResponseEntity.ok(userService.listUsers(sectorId, profileId));
    }

    @PatchMapping("/users/{id}/sector")
    public ResponseEntity<User> assignSector(@PathVariable long id, @RequestBody AssignSectorDto dto) {
        try {
            return ResponseEntity.ok(userService.assignSector(id, dto.sectorId()));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PatchMapping("/users/{id}/profile")
    public ResponseEntity<User> assignProfile(@PathVariable long id, @RequestBody AssignProfileDto dto) {
        try {
            return ResponseEntity.ok(userService.assignProfile(id, dto.profileId()));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
