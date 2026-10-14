package clp.inventory.controller;

import clp.inventory.dto.GoogleAuthDto;
import clp.inventory.dto.LoginDto;
import clp.inventory.service.AuthenticatedUserService;
import clp.inventory.service.UserService;
import clp.inventory.service.auth.AuthenticationService;
import clp.inventory.service.auth.LoginAttemptLimiter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import javax.security.sasl.AuthenticationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthenticationService authenticationService;
  private final UserService userService;
  private final AuthenticatedUserService authenticatedUserService;
  private final LoginAttemptLimiter loginAttemptLimiter;

  public AuthController(
      AuthenticationService authenticationService,
      UserService userService,
      AuthenticatedUserService authenticatedUserService,
      LoginAttemptLimiter loginAttemptLimiter) {
    this.authenticationService = authenticationService;
    this.userService = userService;
    this.authenticatedUserService = authenticatedUserService;
    this.loginAttemptLimiter = loginAttemptLimiter;
  }

  @PostMapping("/google")
  public ResponseEntity<Object> loginWithGoogle(@RequestBody GoogleAuthDto googleAuthDto) {
    if (googleAuthDto == null
        || googleAuthDto.credential() == null
        || googleAuthDto.credential().isBlank()) {
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

  @PostMapping("/login")
  public ResponseEntity<Object> loginWithPassword(
      @RequestBody LoginDto loginDto, HttpServletRequest request) {
    if (loginDto == null
        || loginDto.username() == null
        || loginDto.username().isBlank()
        || loginDto.password() == null
        || loginDto.password().isBlank()) {
      return ResponseEntity.badRequest().body("Missing credentials");
    }

    // O IP é o do peer, ou o do X-Forwarded-For quando server.forward-headers-strategy está ligado.
    String ip = request.getRemoteAddr();

    // Antes de conferir a senha: o bloqueio vale mesmo com a senha certa e poupa o BCrypt.
    var blocked = loginAttemptLimiter.blockedFor(ip, loginDto.username());
    if (blocked.isPresent()) {
      return tooManyAttempts(blocked.get());
    }

    try {
      var response = authenticationService.authenticateWithPassword(loginDto);
      loginAttemptLimiter.recordSuccess(loginDto.username());

      return ResponseEntity.ok().body(response);
    } catch (AuthenticationException e) {
      loginAttemptLimiter.recordFailure(ip, loginDto.username());

      // Mensagem única: não revela se o usuário existe nem se a conta tem senha.
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
    }
  }

  @GetMapping("/me")
  public ResponseEntity<Object> getAuthenticatedUser(
      @RequestHeader("Authorization") String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return ResponseEntity.badRequest().body("Invalid authorization header");
    }

    try {
      var user = userService.getCurrentUser(authHeader);
      return ResponseEntity.ok().body(authenticatedUserService.getById(user.getId()));
    } catch (RuntimeException e) {
      return ResponseEntity.badRequest().body("Invalid authorization header");
    }
  }

  private ResponseEntity<Object> tooManyAttempts(Duration remaining) {
    // Arredonda para cima: dizer 0s faria o cliente tentar de novo e ser barrado outra vez.
    long seconds = Math.max(1, (remaining.toMillis() + 999) / 1000);

    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
        .body("Too many attempts");
  }
}
