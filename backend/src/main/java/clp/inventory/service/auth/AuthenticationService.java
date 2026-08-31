package clp.inventory.service.auth;

import clp.inventory.dto.AuthDto;
import clp.inventory.dto.AuthResponseDTO;
import clp.inventory.exception.VerifyEmailException;
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

    public AuthResponseDTO authenticate(AuthDto authDto) throws AuthenticationException, VerifyEmailException {
        var user = userRepository.findByEmail(authDto.email())
                .orElseThrow(() -> new AuthenticationException("User with email " + authDto.email() + " not found"));

        var isPasswordMatches = passwordEncoder.matches(authDto.password(), user.getPassword());

        if (!isPasswordMatches) {
            throw new AuthenticationException("Invalid password");
        }

        if (!user.isVerified()) {
            userService.sendEmailVerification(authDto.email());
            throw new VerifyEmailException("User is not verified");
        }

        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        var token = JWT.create().withIssuer("inventory")
                .withSubject(user.getId().toString())
                .withExpiresAt(Instant.now().plus(Duration.ofHours(24)))
                .sign(algorithm);

        return new AuthResponseDTO(token, user);
    }
}
