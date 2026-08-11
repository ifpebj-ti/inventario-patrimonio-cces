package clp.inventory.service;

// Importa DTOs para o usuário.
import clp.inventory.dto.UserDto;
// Importa modelos de dados para tipo de token, usuário e tokens de usuário.
import clp.inventory.model.TokenType;
import clp.inventory.model.User;
import clp.inventory.model.UserTokens;
// Importa repositórios para acesso a dados de usuário e tokens de usuário.
import clp.inventory.repository.UserRepository;
import clp.inventory.repository.UserTokensRepository;
// Importa classes do Auth0 JWT para manipulação de tokens.
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
// Importa anotações do Spring para injeção de valores de propriedades, autenticação e marcação de serviço e transação.
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Importa classes para manipulação de tempo, coleções, optionals e UUIDs.
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException; // Importado mas não utilizado neste código.

// Anotação que marca esta classe como um serviço Spring.
// Isso significa que o Spring a gerenciará como um bean e poderá injetá-la em outros componentes.
@Service
public class UserService {

    // Injeção de dependência do repositório de usuários.
    private final UserRepository userRepository;
    // Injeção de dependência do serviço de e-mail.
    private final EmailService emailService;
    // Injeção de dependência do codificador de senhas (BCryptPasswordEncoder).
    private final PasswordEncoder passwordEncoder;
    // Injeção de dependência do repositório de tokens de usuário.
    private final UserTokensRepository userTokensRepository;

    // Injeta o valor da propriedade "security.token.secret" (definida em arquivos de configuração)
    // na variável 'secretKey'. Esta chave é essencial para assinar JWTs.
    @Value("${security.token.secret}")
    private String secretKey; // A chave secreta para a geração e validação de JWT.

    // Construtor da classe UserService.
    // O Spring injetará automaticamente as dependências necessárias.
    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            UserTokensRepository userTokensRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.userTokensRepository = userTokensRepository;
    }

    /**
     * Cria um novo usuário no sistema.
     * Verifica se já existe um usuário com o mesmo e-mail, codifica a senha e envia um e-mail de verificação.
     *
     * @param userDto O DTO contendo os dados do novo usuário.
     * @return O objeto User recém-criado e persistido.
     * @throws RuntimeException Se um usuário com o mesmo e-mail já existir.
     */
    public User createUser(UserDto userDto) {

        // Verifica se já existe um usuário com o e-mail fornecido.
        // Se sim, lança uma RuntimeException.
        userRepository.findByEmail(userDto.email())
                .ifPresent(user -> {
                    throw new RuntimeException("User with email " + userDto.email() + " already exists");
                });

        // Codifica a senha fornecida no DTO usando o PasswordEncoder.
        var password = passwordEncoder.encode(userDto.password());

        // Cria uma nova instância de User com os dados do DTO e a senha codificada.
        // O campo 'verified' é definido como 'false' inicialmente.
        User user = new User(
                userDto.name(),
                userDto.email(),
                password,
                false, // O usuário não é verificado na criação.
                userDto.telephone()
        );

        // Gera um token de verificação único.
        String token = UUID.randomUUID().toString();
        // Salva o novo usuário no banco de dados.
        User savedUser = userRepository.save(user);

        // Cria um novo token de usuário para verificação de e-mail, associando-o ao usuário salvo.
        UserTokens userTokens = new UserTokens(token, TokenType.VERIFICATION, savedUser);
        // Salva o token de usuário no banco de dados.
        userTokensRepository.save(userTokens);
        // Envia o e-mail de verificação para o usuário.
        emailService.sendVerificationEmail(savedUser.getEmail(), token);

        // Retorna o usuário salvo.
        return savedUser;
    }

    /**
     * Verifica a conta de um usuário usando um token de verificação.
     *
     * @param token O token de verificação recebido pelo usuário.
     * @return O objeto User com o status 'verified' definido como true.
     * @throws RuntimeException Se o token não for encontrado, estiver expirado ou o usuário associado não for encontrado.
     */
    public User verifyUser(String token) {
        // Busca o token de usuário pelo valor do token. Se não encontrado, retorna null.
        UserTokens userTokens = userTokensRepository.findByToken(token).orElse(null);
        // Se o token não for encontrado, imprime uma mensagem e lança uma exceção.
        if (userTokens == null) {
            System.out.print("Token not found"); // Saída para o console para depuração.
            throw new RuntimeException("User not found"); // Mensagem de erro genérica para o cliente.
        }

        // Verifica se o token expirou.
        if (userTokens.getExpiration().isBefore(LocalDateTime.now())) {
            System.out.print("Token is expired"); // Saída para o console para depuração.
            System.out.print(userTokens.getExpiration().isAfter(LocalDateTime.now())); // Saída para o console para depuração.
            throw new RuntimeException("Expired token"); // Lança uma exceção se o token expirou.
        }

        // Busca o usuário associado ao token.
        User user = userRepository.findUserById(userTokens.getUser().getId()).orElse(null);
        // Asserção: garante que o usuário não é nulo. Se for, indica um problema lógico inesperado.
        assert user != null : "User not found";
        // Define o status 'verified' do usuário como true.
        user.setVerified(true);
        // Salva as alterações no usuário no banco de dados.
        return userRepository.save(user);
    }

    /**
     * Solicita o envio de um e-mail de redefinição de senha para o endereço fornecido.
     * Gera um novo token de redefinição de senha e o associa ao usuário.
     * A anotação @Transactional garante que as operações sejam atômicas.
     *
     * @param email O e-mail do usuário que solicitou a redefinição de senha.
     * @throws RuntimeException Se o usuário não for encontrado.
     */
    @Transactional // Garante que a operação seja executada dentro de uma transação.
    public void askPasswordResetEmail(String email) {
        // Gera um token único para redefinição de senha.
        String token = UUID.randomUUID().toString();
        // Busca o usuário pelo e-mail.
        userRepository.findByEmail(email).ifPresent(user -> {
            // Este bloco está vazio; a lógica de envio e salvamento do token está fora do ifPresent,
            // o que significa que o Optional está sendo manipulado em duas etapas. Pode ser simplificado.
        });
        Optional<User> userOpt = userRepository.findByEmail(email);
        // Se o usuário for encontrado:
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // Envia o e-mail de redefinição de senha para o usuário.
            emailService.sendPasswordResetEmail(user.getEmail(), token);
            // Cria e salva o token de redefinição de senha associado ao usuário.
            UserTokens userTokens = new UserTokens(token, TokenType.RESETPASSWORD, user);
            userTokensRepository.save(userTokens);
            return; // Retorna após o processamento bem-sucedido.
        }
        // Se o usuário não for encontrado, lança uma exceção.
        throw new RuntimeException("User not found");
    }

    /**
     * Redefine a senha de um usuário usando um token de redefinição.
     * Verifica a validade do token e atualiza a senha do usuário.
     * A anotação @Transactional garante que as operações sejam atômicas.
     *
     * @param token O token de redefinição de senha.
     * @param newPassword A nova senha a ser definida.
     * @return O objeto User com a senha atualizada.
     * @throws RuntimeException Se o token for inválido, já utilizado ou expirado.
     */
    @Transactional // Garante que a operação seja executada dentro de uma transação.
    public User resetPassword(String token, String newPassword) {
        // Busca o token de usuário pelo valor do token.
        // Se não encontrado, lança uma exceção.
        UserTokens userToken = userTokensRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token de redefinição inválido ou já utilizado."));

        // Verifica se o token de redefinição expirou.
        if (userToken.getExpiration().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token expirado. Por favor, solicite um novo link.");
        }

        // Obtém o usuário associado ao token.
        User user = userToken.getUser();
        // Codifica a nova senha e a define para o usuário.
        user.setPassword(passwordEncoder.encode(newPassword));
        // Salva as alterações na senha do usuário no banco de dados.
        User savedUser = userRepository.save(user);
        // Exclui o token de redefinição de senha após o uso para evitar reutilização.
        userTokensRepository.delete(userToken);

        // Retorna o usuário com a senha atualizada.
        return savedUser;
    }

    /**
     * Reenvia um e-mail de verificação para um usuário existente.
     * Gera um novo token de verificação e o associa ao usuário.
     *
     * @param email O e-mail do usuário para quem o e-mail de verificação será reenviado.
     * @throws RuntimeException Se o usuário não for encontrado.
     */
    public void sendEmailVerification(String email) {
        // Busca o usuário pelo e-mail.
        Optional<User> userOpt = userRepository.findByEmail(email);
        // Se o usuário for encontrado:
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // Gera um novo token de verificação.
            String token = UUID.randomUUID().toString();
            // Cria e salva o novo token de verificação associado ao usuário.
            UserTokens userTokens = new UserTokens(token, TokenType.VERIFICATION, user);
            userTokensRepository.save(userTokens);
            // Envia o novo e-mail de verificação.
            emailService.sendVerificationEmail(user.getEmail(), token);
            return; // Retorna após o envio bem-sucedido.
        }
        // Se o usuário não for encontrado, lança uma exceção.
        throw new RuntimeException("User not found");
    }

    /**
     * Obtém o usuário autenticado a partir de um token JWT.
     *
     * @param token O token JWT (pode incluir "Bearer ").
     * @return O objeto User correspondente ao ID no token.
     * @throws RuntimeException Se o usuário não for encontrado ou o token for inválido.
     */
    public User getCurrentUser(String token) {
        // Remove o prefixo "Bearer " do token.
        token = token.replace("Bearer ", "");
        // Cria um algoritmo HMAC256 usando a chave secreta.
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        // Verifica o token e extrai o "subject" (que é o ID do usuário).
        String uid = JWT.require(algorithm).withIssuer("inventory").build().verify(token).getSubject();

        // Busca o usuário pelo ID extraído.
        var user = userRepository.findById(Long.parseLong(uid));

        // Retorna o usuário se encontrado, ou lança uma exceção.
        return user.orElseThrow(() -> new RuntimeException("User not found"));
    }

    /**
     * Lista todos os usuários no sistema.
     * Este método inclui um trecho de código de teste para demonstração de como obter o ID do usuário
     * autenticado a partir do SecurityContextHolder (embora não seja usado para filtrar a lista aqui).
     *
     * @return Uma lista de todos os objetos User.
     */
    public List<User> listAllUsers() {

        // Teste para buscar o id do usuário no token
        // Obtém o objeto Authentication do contexto de segurança atual.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // Extrai o principal (geralmente o ID do usuário ou nome de usuário) do objeto Authentication.
        // Neste contexto, é uma String que representa o ID do usuário.
        String userId = (String) authentication.getPrincipal();
        // A variável userId contém o ID do usuário autenticado para fins de demonstração.
        // Se a intenção fosse listar apenas os usuários relacionados ao 'userId', a lógica de consulta
        // do repositório precisaria ser ajustada (ex: userRepository.findByCreatorId(Long.parseLong(userId))).

        // Retorna todos os usuários encontrados no repositório.
        return userRepository.findAll();
    }

    /**
     * Busca um usuário pelo seu ID.
     *
     * @param userId O ID do usuário a ser buscado.
     * @return O objeto User encontrado.
     * @throws RuntimeException Se o usuário não for encontrado.
     */
    public User findUserById(long userId) {
        // Busca o usuário pelo ID. Se não encontrado, lança uma exceção.
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
    }

}