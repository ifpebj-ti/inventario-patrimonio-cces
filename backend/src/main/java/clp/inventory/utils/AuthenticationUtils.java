package clp.inventory.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationUtils {

    @Value("${security.token.secret}")
    private String secretKey;

    public AuthenticationUtils() {}

    public String getUserIdFromToken(String token) {
        token = token.replace("Bearer ", "");
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        return JWT.require(algorithm).withIssuer("inventory").build().verify(token).getSubject();
    }
}
