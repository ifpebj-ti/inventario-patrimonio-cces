package clp.inventory.controller;

// Importa a classe AuthDto, que provavelmente representa os dados de autenticação (usuário/senha).
import clp.inventory.dto.AuthDto;
// Importa a classe ResetPasswordDto, usada para receber os dados de redefinição de senha.
import clp.inventory.dto.ResetPasswordDto;
// Importa a exceção personalizada VerifyEmailException, que pode ser lançada quando o e-mail não foi verificado.
import clp.inventory.exception.VerifyEmailException;
// Importa o serviço de e-mail, responsável por operações relacionadas ao envio de e-mails.
import clp.inventory.service.EmailService;
// Importa o serviço de usuário, responsável por operações relacionadas a usuários.
import clp.inventory.service.UserService;
// Importa o serviço de autenticação, responsável pela lógica de autenticação.
import clp.inventory.service.auth.AuthenticationService;
// Importa HttpStatus do Spring, para definir os códigos de status HTTP das respostas.
import org.springframework.http.HttpStatus;
// Importa ResponseEntity do Spring, para criar respostas HTTP personalizadas.
import org.springframework.http.ResponseEntity;
// Importa as anotações do Spring para controladores REST.
import org.springframework.web.bind.annotation.*;

// Importa a exceção AuthenticationException do pacote javax.security.sasl.
import javax.security.sasl.AuthenticationException;

// Anotação que indica que esta classe é um controlador REST.
@RestController
// Anotação que mapeia todas as requisições para "/auth" para este controlador.
@RequestMapping("/auth")
// Anotação que permite requisições de qualquer origem (CORS habilitado para todas as origens).
@CrossOrigin(origins = "*")
public class AuthController {

    // Injeção de dependência do serviço de autenticação.
    private final AuthenticationService authenticationService;
    // Injeção de dependência do serviço de usuário.
    private final UserService userService;
    // Injeção de dependência do serviço de e-mail.
    private final EmailService emailService;

    // Construtor do controlador, usado para injetar as dependências (AuthenticationService, UserService, EmailService).
    public AuthController(AuthenticationService authenticationService, UserService userService, EmailService emailService) {
        this.authenticationService = authenticationService;
        this.userService = userService;
        this.emailService = emailService;
    }

    // Mapeia requisições POST para "/auth/login".
    @PostMapping("/login")
    // O método 'login' recebe um objeto AuthDto do corpo da requisição.
    public ResponseEntity<Object> login(@RequestBody AuthDto authDto) {
        try {
            // Tenta autenticar o usuário usando o serviço de autenticação.
            var response = authenticationService.authenticate(authDto);

            // Se a autenticação for bem-sucedida, retorna uma resposta HTTP 200 OK com o corpo da resposta.
            return ResponseEntity.ok().body(response);
        } catch (AuthenticationException e) {
            // Captura a exceção AuthenticationException (provavelmente para credenciais inválidas).
            // Retorna uma resposta HTTP 401 UNAUTHORIZED com uma mensagem de erro.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid login or password");
        } catch (VerifyEmailException e) {
            // Captura a exceção VerifyEmailException (e-mail não verificado).
            // Retorna uma resposta HTTP 401 UNAUTHORIZED com uma mensagem de erro.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Email not verified");
        }
    }

    // Mapeia requisições GET para "/auth/me".
    @GetMapping("/me")
    // O método 'getAuthenticatedUser' espera um cabeçalho "Authorization".
    public ResponseEntity<Object> getAuthenticatedUser(@RequestHeader("Authorization") String authHeader) {
        // Verifica se o cabeçalho de autorização é nulo ou não começa com "Bearer ".
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // Se inválido, retorna uma resposta HTTP 400 BAD REQUEST.
            return ResponseEntity.badRequest().body("Invalid authorization header");
        }

        try {
            // Tenta obter o usuário autenticado usando o serviço de usuário e o cabeçalho de autorização.
            var user = userService.getCurrentUser(authHeader);
            // Se bem-sucedido, retorna uma resposta HTTP 200 OK com os dados do usuário.
            return ResponseEntity.ok().body(user);
        } catch (RuntimeException e) {
            // Captura qualquer RuntimeException (por exemplo, token inválido ou expirado).
            // Retorna uma resposta HTTP 400 BAD REQUEST com uma mensagem de erro.
            return ResponseEntity.badRequest().body("Invalid authorization header");
        }
    }

    // Mapeia requisições GET para "/auth/verify/{token}".
    @GetMapping("/verify/{token}")
    // O método 'verifyEmail' extrai o token da URL.
    public ResponseEntity<Object> verifyEmail(@PathVariable String token) {
        try {
            // Tenta verificar o usuário usando o token através do serviço de usuário.
            var response = userService.verifyUser(token);
            // Se bem-sucedido, retorna uma resposta HTTP 200 OK com a resposta do serviço.
            return ResponseEntity.ok().body(response);
        } catch (RuntimeException e) {
            // Captura qualquer RuntimeException (por exemplo, token inválido ou expirado para verificação).
            // Retorna uma resposta HTTP 400 BAD REQUEST com uma mensagem de erro.
            return ResponseEntity.badRequest().body("Invalid token");
        }
    }

    // Mapeia requisições GET para "/auth/reset-password".
    @GetMapping("/reset-password")
    // O método 'askPasswordResetEmail' espera um parâmetro de requisição 'email'.
    public ResponseEntity<Object> askPasswordResetEmail(@RequestParam String email) {
        try {
            // Tenta enviar um e-mail de redefinição de senha para o endereço fornecido.
            userService.askPasswordResetEmail(email);
            // Se bem-sucedido, retorna uma resposta HTTP 200 OK com uma mensagem de sucesso.
            return ResponseEntity.ok().body("Password reset email sent to " + email);
        } catch (RuntimeException e) {
            // Captura qualquer RuntimeException (por exemplo, e-mail não encontrado).
            // Retorna uma resposta HTTP 400 BAD REQUEST com uma mensagem de erro.
            return ResponseEntity.badRequest().body("Invalid email");
        }
    }

    // Mapeia requisições PUT para "/auth/reset-password/{token}".
    @PutMapping("/reset-password/{token}")
    // O método 'resetPassword' extrai o token da URL e recebe os dados de redefinição de senha do corpo da requisição.
    public ResponseEntity<Object> resetPassword(@PathVariable String token, @RequestBody ResetPasswordDto requestDto) {
        try {
            // Tenta redefinir a senha do usuário usando o token e a nova senha do DTO.
            var user = userService.resetPassword(token, requestDto.password());
            // Se bem-sucedido, retorna uma resposta HTTP 200 OK com os dados do usuário.
            return ResponseEntity.ok().body(user);
        } catch (RuntimeException e) {
            // Captura qualquer RuntimeException (por exemplo, token inválido ou expirado para redefinição).
            // Retorna uma resposta HTTP 400 BAD REQUEST com uma mensagem de erro.
            return ResponseEntity.badRequest().body("Invalid token");
        }
    }
}