package clp.inventory.controller;

import clp.inventory.dto.AuthResponseDTO;
import clp.inventory.dto.AuthenticatedUserDto;
import clp.inventory.dto.GoogleAuthDto;
import clp.inventory.dto.LoginDto;
import clp.inventory.service.AuthenticatedUserService;
import clp.inventory.service.UserService;
import clp.inventory.service.auth.AuthenticationService;
import clp.inventory.service.auth.LoginAttemptLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import javax.security.sasl.AuthenticationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Autenticação", description = "Login e dados da sessão atual.")
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

  @Operation(
      summary = "Login com Google",
      description =
          "Troca o ID token do Google Identity Services pelo JWT da aplicação (HS256, 24h). Valida assinatura (JWKS do Google), iss, aud, exp e email_verified. É a única forma de um usuário ser criado: quem entra pela primeira vez nasce sem alocação e sem ser administrador, ou seja, sem acesso até ser alocado a um setor. O domínio do e-mail precisa estar em GOOGLE_ALLOWED_DOMAINS (vazio libera todos). Não exige Authorization.",
      security = {})
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Login realizado.",
        content = @Content(schema = @Schema(implementation = AuthResponseDTO.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Credencial ausente ou em branco.",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token do Google inválido (mensagem única para qualquer falha de validação).",
        content = @Content)
  })
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

  @Operation(
      summary = "Login por usuário e senha (conta de sistema)",
      description =
          "Login sem Google, só para a conta de sistema (admin) e as contas de teste. Há bloqueio temporário por usuário depois de falhas seguidas (padrão: 5 falhas em 15 min bloqueiam por 15 min), e o Nginx limita por IP. Não exige Authorization.",
      security = {})
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Login realizado.",
        content = @Content(schema = @Schema(implementation = AuthResponseDTO.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Usuário ou senha ausentes ou em branco.",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Credenciais inválidas (mensagem única: não revela se o usuário existe).",
        content = @Content),
    @ApiResponse(
        responseCode = "429",
        description =
            "Muitas tentativas. O cabeçalho Retry-After traz os segundos até poder tentar de novo.",
        content = @Content)
  })
  @PostMapping("/login")
  public ResponseEntity<Object> loginWithPassword(@RequestBody LoginDto loginDto) {
    if (loginDto == null
        || loginDto.username() == null
        || loginDto.username().isBlank()
        || loginDto.password() == null
        || loginDto.password().isBlank()) {
      return ResponseEntity.badRequest().body("Missing credentials");
    }

    // Antes de conferir a senha: o bloqueio vale mesmo com a senha certa e poupa o BCrypt. O limite
    // por IP não é daqui, é do Nginx (zona auth no /auth/login).
    var blocked = loginAttemptLimiter.blockedFor(loginDto.username());
    if (blocked.isPresent()) {
      return tooManyAttempts(blocked.get());
    }

    try {
      var response = authenticationService.authenticateWithPassword(loginDto);
      loginAttemptLimiter.recordSuccess(loginDto.username());

      return ResponseEntity.ok().body(response);
    } catch (AuthenticationException e) {
      loginAttemptLimiter.recordFailure(loginDto.username());

      // Mensagem única: não revela se o usuário existe nem se a conta tem senha.
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
    }
  }

  @Operation(
      summary = "Usuário autenticado",
      description =
          "Devolve os dados da sessão: o usuário, se é administrador e as alocações (cargo e permissões em cada setor). O frontend usa para montar a interface conforme o acesso.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Dados do usuário autenticado.",
        content = @Content(schema = @Schema(implementation = AuthenticatedUserDto.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Cabeçalho Authorization ausente ou fora do formato Bearer.",
        content = @Content)
  })
  @GetMapping("/me")
  public ResponseEntity<Object> getAuthenticatedUser(
      @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) {
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
