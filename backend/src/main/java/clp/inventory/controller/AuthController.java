package clp.inventory.controller;

import clp.inventory.dto.AuthDto;
import clp.inventory.dto.ResetPasswordDto;
import clp.inventory.exception.VerifyEmailException;
import clp.inventory.service.EmailService;
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
    private final EmailService emailService;

    public AuthController(AuthenticationService authenticationService, UserService userService, EmailService emailService) {
        this.authenticationService = authenticationService;
        this.userService = userService;
        this.emailService = emailService;
    }

    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody AuthDto authDto) {
        try {
            var response = authenticationService.authenticate(authDto);

            return ResponseEntity.ok().body(response);
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid login or password");
        } catch (VerifyEmailException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Email not verified");
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

    @GetMapping("/verify/{token}")
    public ResponseEntity<Object> verifyEmail(@PathVariable String token) {
        try {
            var response = userService.verifyUser(token);
            return ResponseEntity.ok().body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body("Invalid token");
        }
    }

    @GetMapping("/reset-password")
    public ResponseEntity<Object> askPasswordResetEmail(@RequestParam String email) {
        try {
            userService.askPasswordResetEmail(email);
            return ResponseEntity.ok().body("Password reset email sent to " + email);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body("Invalid email");
        }
    }

    @PutMapping("/reset-password/{token}")
    public ResponseEntity<Object> resetPassword(@PathVariable String token, @RequestBody ResetPasswordDto requestDto) {
        try {
            var user = userService.resetPassword(token, requestDto.password());
            return ResponseEntity.ok().body(user);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body("Invalid token");
        }
    }
}
