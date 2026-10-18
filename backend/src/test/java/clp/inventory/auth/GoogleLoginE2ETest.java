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
  @Autowired clp.inventory.repository.SectorRepository sectorRepository;
  @Autowired clp.inventory.repository.SectorAllocationRepository allocationRepository;
  @Autowired clp.inventory.service.AdminUserService adminUserService;

  @Test
  void firstGoogleLoginPreservesPreRegisteredUserProfileAndSectors() throws Exception {
    var admin = new clp.inventory.model.User();
    admin.setName("Admin");
    admin.setEmail("pre-registration-admin@ifpe.edu.br");
    admin.setGoogleId("pre-registration-admin-sub");
    admin.setProfile(
        profileRepository.findAll().stream()
            .filter(p -> p.name().equals("ADMIN_ORGANIZATION"))
            .findFirst()
            .orElseThrow());
    userRepository.save(admin);
    var profile =
        profileRepository.findAll().stream()
            .filter(p -> p.name().equals("GESTOR_SETOR"))
            .findFirst()
            .orElseThrow();
    var sector =
        sectorRepository.save(new clp.inventory.model.Sector("Pre-registration sector", null));
    String email = "pre-registered@ifpe.edu.br";
    var pending =
        adminUserService.create(
            new clp.inventory.dto.CreateUserDto(
                "Nome definido pelo admin", email, profile.id(), java.util.List.of(sector.id())),
            admin.getId());
    var response =
        restTemplate.postForEntity(
            "/auth/google", Map.of("credential", signToken(email)), Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    var sessionUser = (Map<?, ?>) response.getBody().get("user");
    assertThat(sessionUser.get("id")).isEqualTo(pending.getId().intValue());
    assertThat(sessionUser.get("name")).isEqualTo("Nome definido pelo admin");
    assertThat(sessionUser.get("profileId")).isEqualTo((int) profile.id());
    assertThat(sessionUser.get("permissions")).isEqualTo(java.util.List.of("MANAGE_SECTOR"));
    var stored = userRepository.findById(pending.getId()).orElseThrow();
    assertThat(stored.isPendingGoogleLogin()).isFalse();
    assertThat(stored.getGoogleId()).isEqualTo("google-sub-" + email);
    assertThat(allocationRepository.findByUser_Id(pending.getId())).hasSize(1);
  }

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

    // Quem entra pela primeira vez nasce com o perfil de consulta (somente leitura).
    var created = userRepository.findByEmail(ALLOWED_EMAIL).orElseThrow();
    assertThat(created.getProfile().name()).isEqualTo("CONSULTA");
    assertThat(((Number) sessionUser.get("profileId")).longValue())
        .isEqualTo(created.getProfileId());

    var meHeaders = new HttpHeaders();
    meHeaders.setBearerAuth(appToken);
    var meResponse =
        restTemplate.exchange("/auth/me", HttpMethod.GET, new HttpEntity<>(meHeaders), Map.class);

    assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(meResponse.getBody().get("email")).isEqualTo(ALLOWED_EMAIL);
    assertThat(meResponse.getBody()).isEqualTo(sessionUser);
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
