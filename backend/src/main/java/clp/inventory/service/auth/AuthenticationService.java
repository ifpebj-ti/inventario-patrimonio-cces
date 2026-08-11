package clp.inventory.service.auth;

// Importa DTOs para autenticação (AuthDto, AuthResponseDTO).
import clp.inventory.dto.AuthDto;
import clp.inventory.dto.AuthResponseDTO;
// Importa a exceção personalizada VerifyEmailException.
import clp.inventory.exception.VerifyEmailException;
// Importa modelos de dados (TokenType, User, UserTokens).
import clp.inventory.model.TokenType;
import clp.inventory.model.User;
import clp.inventory.model.UserTokens;
// Importa repositórios para acesso a dados (UserRepository, UserTokensRepository).
import clp.inventory.repository.UserRepository;
import clp.inventory.repository.UserTokensRepository;
// Importa serviços (EmailService, UserService).
import clp.inventory.service.EmailService;
import clp.inventory.service.UserService;
// Importa classes do Auth0 JWT para manipulação de tokens.
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
// Importa anotações do Spring para injeção de valores de propriedades e para marcar a classe como um serviço.
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder; // Para codificação/verificação de senhas.
import org.springframework.stereotype.Service; // Anotação para marcar a classe como um serviço Spring.

// Importa AuthenticationException do Javax Security SASL.
import javax.security.sasl.AuthenticationException;
// Importa classes para manipulação de tempo e geração de UUIDs.
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

// Anotação que marca esta classe como um serviço Spring.
// Isso significa que o Spring a gerenciará como um bean e poderá injetá-la em outros componentes.
@Service
public class AuthenticationService {

    // Injeta o valor da propriedade "security.token.secret" (definida em arquivos de configuração)
    // na variável 'secretKey'. Esta chave é essencial para assinar JWTs.
    @Value("${security.token.secret}")
    private String secretKey; // A chave secreta para a geração e validação de JWT.

    // Injeção de dependências dos repositórios e serviços necessários para a autenticação.
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // Usado para codificar e verificar senhas.
    private final UserService userService;         // Usado para operações de usuário, como envio de e-mail de verificação.
    private final EmailService emailService;       // Embora injetado, não é usado diretamente neste método authenticate.

    // Construtor da classe AuthenticationService.
    // O Spring injetará automaticamente as dependências quando criar este serviço.
    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            UserService userService,
            EmailService emailService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userService = userService;
        this.emailService = emailService;
    }

    /**
     * Autentica um usuário com base nas credenciais fornecidas (e-mail e senha).
     *
     * @param authDto Objeto AuthDto contendo o e-mail e a senha do usuário.
     * @return Um AuthResponseDTO contendo o token JWT gerado e os dados do usuário autenticado.
     * @throws AuthenticationException Se o usuário não for encontrado ou a senha for inválida.
     * @throws VerifyEmailException Se o usuário não estiver verificado (e-mail não confirmado).
     */
    public AuthResponseDTO authenticate(AuthDto authDto) throws AuthenticationException, VerifyEmailException {
        // Busca o usuário no banco de dados pelo e-mail fornecido no AuthDto.
        // Se o usuário não for encontrado, lança uma AuthenticationException.
        var user = userRepository.findByEmail(authDto.email())
                .orElseThrow(() -> new AuthenticationException("User with email " + authDto.email() + " not found"));

        // Verifica se a senha fornecida no AuthDto corresponde à senha armazenada (codificada) do usuário.
        // O PasswordEncoder cuida da comparação segura.
        var isPasswordMatches = passwordEncoder.matches(authDto.password(), user.getPassword());

        // Se as senhas não corresponderem, lança uma AuthenticationException.
        if (!isPasswordMatches) {
            throw new AuthenticationException("Invalid password");
        }

        // Verifica se o e-mail do usuário foi verificado.
        if (!user.isVerified()) {
            // Se o e-mail não estiver verificado, tenta reenviar um e-mail de verificação.
            userService.sendEmailVerification(authDto.email());
            // Lança uma VerifyEmailException para indicar que o usuário precisa verificar o e-mail.
            throw new VerifyEmailException("User is not verified");
        }

        // Se o usuário for encontrado, a senha estiver correta e o e-mail verificado,
        // um token JWT é gerado.
        // Cria um algoritmo HMAC256 usando a chave secreta. Este algoritmo é usado para assinar o token.
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        // Constrói o token JWT:
        // - withIssuer("inventory"): Define o emissor do token como "inventory".
        // - withSubject(user.getId().toString()): Define o "assunto" do token como o ID do usuário (convertido para String).
        // - withExpiresAt(Instant.now().plus(Duration.ofHours(24))): Define a data de expiração do token para 24 horas a partir do momento atual.
        // - sign(algorithm): Assina o token usando o algoritmo HMAC256 com a chave secreta.
        var token = JWT.create().withIssuer("inventory")
                .withSubject(user.getId().toString())
                .withExpiresAt(Instant.now().plus(Duration.ofHours(24)))
                .sign(algorithm);

        // Retorna um AuthResponseDTO contendo o token JWT gerado e o objeto User autenticado.
        return new AuthResponseDTO(token, user);
    }
}