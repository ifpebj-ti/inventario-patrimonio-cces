package clp.inventory.controller;

import clp.inventory.dto.GoogleAuthDto;
import clp.inventory.service.UserService;
import clp.inventory.service.auth.AuthenticationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.security.sasl.AuthenticationException;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final UserService userService;

    public AuthController(AuthenticationService authenticationService, UserService userService) {
        this.authenticationService = authenticationService;
        this.userService = userService;
    }

    @PostMapping("/google")
    public ResponseEntity<Object> loginWithGoogle(@RequestBody GoogleAuthDto googleAuthDto) {
        if (googleAuthDto == null || googleAuthDto.credential() == null || googleAuthDto.credential().isBlank()) {
            return ResponseEntity.badRequest().body("Missing Google credential");
        }

        try {
            var response = authenticationService.authenticateWithGoogle(googleAuthDto);

            return ResponseEntity.ok().body(response);
        } catch (AuthenticationException e) {
            // Mensagem única para toda falha de validação, para não revelar qual check falhou.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Google token");
        }
    }

    @GetMapping("/me")
    public ResponseEntity<Object> getAuthenticatedUser(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body("Invalid authorization header");
        }

        try {
            var user = userService.getCurrentUser(authHeader);
            return ResponseEntity.ok().body(user);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body("Invalid authorization header");
        }
    }
}
