package clp.inventory.service.auth;

import clp.inventory.dto.AuthResponseDTO;
import clp.inventory.dto.GoogleAuthDto;
import clp.inventory.model.User;
import clp.inventory.providers.GoogleTokenProvider;
import clp.inventory.service.UserService;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.security.sasl.AuthenticationException;
import java.time.Duration;
import java.time.Instant;

@Service
public class AuthenticationService {

    @Value("${security.token.secret}")
    private String secretKey;

    private final UserService userService;
    private final GoogleTokenProvider googleTokenProvider;

    public AuthenticationService(
            UserService userService,
            GoogleTokenProvider googleTokenProvider
    ) {
        this.userService = userService;
        this.googleTokenProvider = googleTokenProvider;
    }

    public AuthResponseDTO authenticateWithGoogle(GoogleAuthDto googleAuthDto) throws AuthenticationException {
        var googleUser = googleTokenProvider.verify(googleAuthDto.credential());
        var user = userService.findOrCreateGoogleUser(googleUser);

        return new AuthResponseDTO(issueToken(user), user);
    }

    /**
     * Emite o token da aplicação. O issuer "inventory" é obrigatório: o
     * SecurityFilter não o confere, mas AuthenticationUtils e getCurrentUser
     * conferem, então um token sem ele passaria pelo filtro e só quebraria
     * depois, dentro do controller.
     */
    private String issueToken(User user) {
        Algorithm algorithm = Algorithm.HMAC256(secretKey);

        return JWT.create().withIssuer("inventory")
                .withSubject(user.getId().toString())
                .withExpiresAt(Instant.now().plus(Duration.ofHours(24)))
                .sign(algorithm);
    }
}
