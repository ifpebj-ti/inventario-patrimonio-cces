package clp.inventory.providers;

import clp.inventory.dto.GoogleUserInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import javax.security.sasl.AuthenticationException;
import java.time.Duration;
import java.util.List;
import java.util.Set;

@Service
public class GoogleTokenProvider {

    // O Google emite o claim "iss" nas duas grafias; ambas são válidas.
    private static final Set<String> GOOGLE_ISSUERS =
            Set.of("https://accounts.google.com", "accounts.google.com");

    // im_user.name é VARCHAR(100).
    private static final int MAX_NAME_LENGTH = 100;

    private final String clientId;
    private final JwtDecoder decoder;

    public GoogleTokenProvider(
            @Value("${google.oauth.client-id}") String clientId,
            @Value("${google.oauth.jwk-set-uri}") String jwkSetUri
    ) {
        this.clientId = clientId;

        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                // O RestTemplate padrão não tem timeout: uma conexão pendurada com o
                // Google prenderia uma thread do Tomcat indefinidamente.
                .restOperations(new RestTemplateBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofSeconds(5))
                        .build())
                .build();

        nimbusDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtClaimValidator<String>("iss", GOOGLE_ISSUERS::contains),
                // O claim "aud" sempre chega como coleção, mesmo quando o Google manda
                // uma string: tipar como String faria o validador nunca casar e virar no-op.
                new JwtClaimValidator<List<String>>("aud",
                        aud -> aud != null && aud.contains(clientId))
        ));

        this.decoder = nimbusDecoder;
    }

    public GoogleUserInfo verify(String credential) throws AuthenticationException {
        // Sem client id configurado o validador de "aud" rejeitaria tudo. Falhar explícito
        // aqui evita diagnosticar isso como token inválido.
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("google.oauth.client-id não está configurado");
        }

        try {
            Jwt jwt = decoder.decode(credential);

            String email = jwt.getClaimAsString("email");
            if (email == null || email.isBlank() || !isEmailVerified(jwt)) {
                throw new AuthenticationException("Google account email is not verified");
            }

            return new GoogleUserInfo(jwt.getSubject(), email, resolveName(jwt, email));
        } catch (JwtException e) {
            throw new AuthenticationException("Invalid Google ID token");
        }
    }

    private boolean isEmailVerified(Jwt jwt) {
        Object claim = jwt.getClaim("email_verified");
        return Boolean.TRUE.equals(claim) || "true".equals(String.valueOf(claim));
    }

    private String resolveName(Jwt jwt, String fallback) {
        String name = jwt.getClaimAsString("name");
        if (name == null || name.isBlank()) {
            name = fallback;
        }

        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }
}
