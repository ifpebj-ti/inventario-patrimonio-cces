package clp.inventory.auth;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.repository.UserRepository;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sobe a aplicação inteira (Postgres real via Testcontainers, HTTP real via TestRestTemplate) e
 * finge os dois lados que envolvem o Google: um client id inventado e um servidor JWKS local
 * assinando os tokens de teste. O GoogleTokenProvider não tem como distinguir isso de um Google de
 * verdade — ele só confere consistência criptográfica e de claims.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class GoogleLoginE2ETest {

  private static final String CLIENT_ID = "test-client-id";
  private static final String ALLOWED_EMAIL = "aluno@ifpe.edu.br"; // domínio já é o default
  private static final String REJECTED_EMAIL = "estranho@gmail.com";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  static HttpServer jwksServer;
  static RSAKey rsaKey;

  @BeforeAll
  static void startFakeJwks() throws Exception {
    rsaKey = new RSAKeyGenerator(2048).keyID("test-key").generate();

    jwksServer = HttpServer.create(new InetSocketAddress(0), 0);
    jwksServer.createContext(
        "/certs",
        exchange -> {
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
    registry.add(
        "google.oauth.jwk-set-uri",
        () -> "http://localhost:" + jwksServer.getAddress().getPort() + "/certs");
    registry.add("security.token.secret", () -> "test-secret-de-pelo-menos-32-bytes-aqui");
  }

  @Autowired TestRestTemplate restTemplate;

  @Autowired UserRepository userRepository;

  @Autowired clp.inventory.repository.ProfileRepository profileRepository;

  private String signToken(String email) throws JOSEException {
    var now = Instant.now();
    var claims =
        new JWTClaimsSet.Builder()
            .subject("google-sub-" + email)
            .issuer("https://accounts.google.com")
            .audience(CLIENT_ID)
            .claim("email", email)
            .claim("email_verified", true)
            .claim("name", "Test User")
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(3600)))
            .build();

    var signedJWT =
        new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
    signedJWT.sign(new RSASSASigner(rsaKey));
    return signedJWT.serialize();
  }

  @Test
  void successfulLogin_createsUserAndReturnsValidToken() throws Exception {
    String idToken = signToken(ALLOWED_EMAIL);

    var response =
        restTemplate.postForEntity("/auth/google", Map.of("credential", idToken), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String appToken = (String) response.getBody().get("token");
    assertThat(appToken).isNotBlank();
    @SuppressWarnings("unchecked")
    Map<String, Object> sessionUser = (Map<String, Object>) response.getBody().get("user");
    assertThat(sessionUser)
        .containsEntry("email", ALLOWED_EMAIL)
        .containsEntry("sectorIds", java.util.List.of())
        .containsEntry("permissions", java.util.List.of())
        .doesNotContainKeys("googleId", "systemUser", "passwordHash", "profile");

    // Quem entra pela primeira vez nasce sem perfil: um administrador atribui o acesso depois.
    var created = userRepository.findByEmail(ALLOWED_EMAIL).orElseThrow();
    assertThat(created.getProfile()).isNull();
    assertThat(sessionUser.get("profileId")).isNull();

    var meHeaders = new HttpHeaders();
    meHeaders.setBearerAuth(appToken);
    var meResponse =
        restTemplate.exchange("/auth/me", HttpMethod.GET, new HttpEntity<>(meHeaders), Map.class);

    assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(meResponse.getBody().get("email")).isEqualTo(ALLOWED_EMAIL);
    assertThat(meResponse.getBody()).isEqualTo(sessionUser);
  }

  @Test
  void userCreatedByGoogleLoginHasNoPermissionsUntilAnAdminAssignsAProfile() throws Exception {
    var admin = new clp.inventory.model.User();
    admin.setName("Admin");
    admin.setEmail("perfil-admin@ifpe.edu.br");
    admin.setGoogleId("google-sub-perfil-admin@ifpe.edu.br");
    admin.setProfile(
        profileRepository.findAll().stream()
            .filter(p -> p.name().equals("ADMIN_ORGANIZATION"))
            .findFirst()
            .orElseThrow());
    userRepository.save(admin);
    String adminToken =
        (String)
            restTemplate
                .postForEntity(
                    "/auth/google",
                    Map.of("credential", signToken("perfil-admin@ifpe.edu.br")),
                    Map.class)
                .getBody()
                .get("token");

    String email = "sem-perfil@ifpe.edu.br";
    var login =
        restTemplate.postForEntity(
            "/auth/google", Map.of("credential", signToken(email)), Map.class);
    String token = (String) login.getBody().get("token");
    var created = userRepository.findByEmail(email).orElseThrow();

    // Sem perfil: nenhuma permissão e nenhuma rota de administração.
    var meBefore =
        restTemplate.exchange(
            "/auth/me", HttpMethod.GET, new HttpEntity<>(bearer(token)), Map.class);
    assertThat(meBefore.getBody().get("profileId")).isNull();
    assertThat(meBefore.getBody().get("permissions")).isEqualTo(java.util.List.of());
    assertThat(
            restTemplate
                .exchange("/users", HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    // Um administrador atribui o perfil e o mesmo usuário passa a ter as permissões dele.
    var consulta =
        profileRepository.findAll().stream()
            .filter(p -> p.name().equals("GESTOR_SETOR"))
            .findFirst()
            .orElseThrow();
    var assigned =
        restTemplate.exchange(
            "/users/" + created.getId() + "/profile",
            HttpMethod.PATCH,
            new HttpEntity<>(Map.of("profileId", consulta.id()), bearer(adminToken)),
            Map.class);
    assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);
    var meAfter =
        restTemplate.exchange(
            "/auth/me", HttpMethod.GET, new HttpEntity<>(bearer(token)), Map.class);
    assertThat(meAfter.getBody().get("permissions")).isEqualTo(java.util.List.of("MANAGE_SECTOR"));
  }

  private HttpHeaders bearer(String token) {
    var headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    return headers;
  }

  @Test
  void disallowedDomain_returns401AndDoesNotCreateUser() throws Exception {
    String idToken = signToken(REJECTED_EMAIL);

    var response =
        restTemplate.postForEntity("/auth/google", Map.of("credential", idToken), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(userRepository.findByEmail(REJECTED_EMAIL)).isEmpty();
  }
}
