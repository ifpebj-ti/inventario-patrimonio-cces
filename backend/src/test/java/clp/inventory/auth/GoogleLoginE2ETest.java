package clp.inventory.auth;

import clp.inventory.repository.UserRepository;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sobe a aplicação inteira (Postgres real via Testcontainers, HTTP real via
 * TestRestTemplate) e finge os dois lados que envolvem o Google: um client id
 * inventado e um servidor JWKS local assinando os tokens de teste. O
 * GoogleTokenProvider não tem como distinguir isso de um Google de verdade —
 * ele só confere consistência criptográfica e de claims.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GoogleLoginE2ETest {

    private static final String CLIENT_ID = "test-client-id";
    private static final String ALLOWED_EMAIL = "aluno@ifpe.edu.br"; // domínio já é o default
    private static final String REJECTED_EMAIL = "estranho@gmail.com";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    static HttpServer jwksServer;
    static RSAKey rsaKey;

    @BeforeAll
    static void startFakeJwks() throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("test-key").generate();

        jwksServer = HttpServer.create(new InetSocketAddress(0), 0);
        jwksServer.createContext("/certs", exchange -> {
            byte[] body = ("{\"keys\":[" + rsaKey.toPublicJWK().toJSONString() + "]}").getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        jwksServer.start();
    }

    @AfterAll
    static void stopFakeJwks() {
        jwksServer.stop(0);
    }

    @DynamicPropertySource
    static void authProperties(DynamicPropertyRegistry registry) {
        registry.add("google.oauth.client-id", () -> CLIENT_ID);
        registry.add("google.oauth.jwk-set-uri",
                () -> "http://localhost:" + jwksServer.getAddress().getPort() + "/certs");
        registry.add("security.token.secret", () -> "test-secret-de-pelo-menos-32-bytes-aqui");
    }

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository userRepository;

    private String signToken(String email) throws JOSEException {
        var now = Instant.now();
        var claims = new JWTClaimsSet.Builder()
                .subject("google-sub-" + email)
                .issuer("https://accounts.google.com")
                .audience(CLIENT_ID)
                .claim("email", email)
                .claim("email_verified", true)
                .claim("name", "Test User")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .build();

        var signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
        signedJWT.sign(new RSASSASigner(rsaKey));
        return signedJWT.serialize();
    }

    @Test
    void successfulLogin_createsUserAndReturnsValidToken() throws Exception {
        String idToken = signToken(ALLOWED_EMAIL);

        var response = restTemplate.postForEntity(
                "/auth/google", Map.of("credential", idToken), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String appToken = (String) response.getBody().get("token");
        assertThat(appToken).isNotBlank();

        assertThat(userRepository.findByEmail(ALLOWED_EMAIL)).isPresent();

        var meHeaders = new HttpHeaders();
        meHeaders.setBearerAuth(appToken);
        var meResponse = restTemplate.exchange(
                "/auth/me", HttpMethod.GET, new HttpEntity<>(meHeaders), Map.class);

        assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meResponse.getBody().get("email")).isEqualTo(ALLOWED_EMAIL);
    }

    @Test
    void disallowedDomain_returns401AndDoesNotCreateUser() throws Exception {
        String idToken = signToken(REJECTED_EMAIL);

        var response = restTemplate.postForEntity(
                "/auth/google", Map.of("credential", idToken), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(userRepository.findByEmail(REJECTED_EMAIL)).isEmpty();
    }
}
