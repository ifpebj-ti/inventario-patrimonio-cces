package clp.inventory.providers;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JWTProvider {

    private static final Logger log = LoggerFactory.getLogger(JWTProvider.class);

    @Value("${security.token.secret}")
    private String secretKey;

    public String validateToken(String token) {
        token = token.replace("Bearer ", "");
        Algorithm algorithm = Algorithm.HMAC256(secretKey);

        try {
            // O issuer precisa ser conferido aqui também: sem isso o AuthenticationUtils,
            // que confere, rejeitaria dentro do controller um token já aceito pelo filtro.
            var subject = JWT.require(algorithm)
                    .withIssuer("inventory")
                    .build()
                    .verify(token)
                    .getSubject();

            return subject;
        } catch (JWTVerificationException e) {
            log.debug("Token inválido: {}", e.getMessage());
            return "";
        }
    }
}
