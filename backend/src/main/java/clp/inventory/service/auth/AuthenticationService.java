package clp.inventory.service.auth;

import clp.inventory.dto.AuthDto;
import clp.inventory.dto.AuthResponseDTO;
import clp.inventory.dto.GoogleAuthDto;
import clp.inventory.exception.VerifyEmailException;
import clp.inventory.providers.GoogleTokenProvider;
import clp.inventory.model.TokenType;
import clp.inventory.model.User;
import clp.inventory.model.UserTokens;
import clp.inventory.repository.UserRepository;
import clp.inventory.repository.UserTokensRepository;
import clp.inventory.service.EmailService;
import clp.inventory.service.UserService;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.security.sasl.AuthenticationException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthenticationService {

    @Value("${security.token.secret}")
    private String secretKey;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final EmailService emailService;
    private final GoogleTokenProvider googleTokenProvider;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            UserService userService,
            EmailService emailService,
            GoogleTokenProvider googleTokenProvider
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userService = userService;
        this.emailService = emailService;
        this.googleTokenProvider = googleTokenProvider;
    }

    public AuthResponseDTO authenticate(AuthDto authDto) throws AuthenticationException, VerifyEmailException {
        var user = userRepository.findByEmail(authDto.email())
                .orElseThrow(() -> new AuthenticationException("User with email " + authDto.email() + " not found"));

        // Conta criada via Google não tem senha e nunca pode autenticar por aqui. O guard
        // também protege o matches(), que lança IllegalArgumentException com senha nula —
        // exceção que o controller não captura e viraria 500.
        if (authDto.password() == null || authDto.password().isBlank()
                || user.getPassword() == null || user.getPassword().isBlank()) {
            throw new AuthenticationException("Invalid credentials");
        }

        if (!passwordEncoder.matches(authDto.password(), user.getPassword())) {
            throw new AuthenticationException("Invalid password");
        }

        if (!user.isVerified()) {
            userService.sendEmailVerification(authDto.email());
            throw new VerifyEmailException("User is not verified");
        }

        return new AuthResponseDTO(issueToken(user), user);
    }

    public AuthResponseDTO authenticateWithGoogle(GoogleAuthDto googleAuthDto) throws AuthenticationException {
        var googleUser = googleTokenProvider.verify(googleAuthDto.credential());
        var user = userService.findOrLinkGoogleUser(googleUser);

        return new AuthResponseDTO(issueToken(user), user);
    }

    /**
     * Emite o token da aplicação. Os dois caminhos de login passam por aqui: um token sem
     * o issuer "inventory" atravessaria o SecurityFilter, que não o confere, e só quebraria
     * depois no AuthenticationUtils, que confere.
     */
    private String issueToken(User user) {
        Algorithm algorithm = Algorithm.HMAC256(secretKey);

        return JWT.create().withIssuer("inventory")
                .withSubject(user.getId().toString())
                .withExpiresAt(Instant.now().plus(Duration.ofHours(24)))
                .sign(algorithm);
    }
}
