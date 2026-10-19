package clp.inventory.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import clp.inventory.model.User;
import clp.inventory.repository.UserRepository;
import clp.inventory.service.auth.SystemAdminInitializer;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sobe a aplicação inteira com a conta de sistema habilitada (SYSTEM_ADMIN_PASSWORD preenchida) e
 * confere o login por usuário e senha, a convivência com o login Google e as constraints de
 * integridade de im_user. O servidor JWKS falso é o mesmo esquema do GoogleLoginE2ETest.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class SystemAdminLoginE2ETest {

  private static final String CLIENT_ID = "test-client-id";
  private static final String ADMIN_PASSWORD = "senha-forte-do-admin";

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
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("google.oauth.client-id", () -> CLIENT_ID);
    registry.add(
        "google.oauth.jwk-set-uri",
        () -> "http://localhost:" + jwksServer.getAddress().getPort() + "/certs");
    registry.add("security.token.secret", () -> "test-secret-de-pelo-menos-32-bytes-aqui");
    registry.add("system.admin.username", () -> "admin");
    registry.add("system.admin.password", () -> ADMIN_PASSWORD);
  }

  @Autowired TestRestTemplate restTemplate;
  @Autowired UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;

  private ResponseEntity<String> login(String username, String password) {
    return restTemplate.postForEntity(
        "/auth/login", Map.of("username", username, "password", password), String.class);
  }

  private String signGoogleToken(String email) throws JOSEException {
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

  private SystemAdminInitializer initializer(String username, String password) {
    return new SystemAdminInitializer(userRepository, passwordEncoder, username, password);
  }

  @Test
  void login_validCredentials_returnsTokenThatWorksOnAdminRoutes() {
    var response =
        restTemplate.postForEntity(
            "/auth/login", Map.of("username", "admin", "password", ADMIN_PASSWORD), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String token = (String) response.getBody().get("token");
    assertThat(token).isNotBlank();
    @SuppressWarnings("unchecked")
    Map<String, Object> user = (Map<String, Object>) response.getBody().get("user");
    assertThat(user.get("email")).isEqualTo("admin");
    assertThat(user)
        .containsEntry("sectorIds", java.util.List.of())
        .containsEntry("administrator", true)
        .containsEntry("allocations", java.util.List.of())
        .doesNotContainKeys("passwordHash", "googleId", "systemUser", "profile", "permissions");

    var headers = new HttpHeaders();
    headers.setBearerAuth(token);

    var me =
        restTemplate.exchange("/auth/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(me.getBody().get("email")).isEqualTo("admin");
    assertThat(me.getBody()).isEqualTo(user);

    var users =
        restTemplate.exchange("/users", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    assertThat(users.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void login_isCaseInsensitiveOnUsername() {
    assertThat(login("ADMIN", ADMIN_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void login_wrongPasswordAndUnknownUser_return401WithSameMessage() {
    var wrongPassword =
        restTemplate.postForEntity(
            "/auth/login",
            Map.of("username", "admin", "password", "senha-errada-123"),
            String.class);
    var unknownUser =
        restTemplate.postForEntity(
            "/auth/login",
            Map.of("username", "naoexiste", "password", ADMIN_PASSWORD),
            String.class);

    assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(unknownUser.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(wrongPassword.getBody()).isEqualTo(unknownUser.getBody());
  }

  @Test
  void login_blankFields_return400() {
    var response =
        restTemplate.postForEntity(
            "/auth/login", Map.of("username", "", "password", ""), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void login_withExpiredBearerStillReachesTheEndpoint() {
    var headers = new HttpHeaders();
    headers.setBearerAuth("token-invalido-ou-expirado");
    headers.setContentType(MediaType.APPLICATION_JSON);

    var response =
        restTemplate.postForEntity(
            "/auth/login",
            new HttpEntity<>(Map.of("username", "admin", "password", ADMIN_PASSWORD), headers),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void systemAccount_cannotSignInWithGoogle() throws Exception {
    var response =
        restTemplate.postForEntity(
            "/auth/google", Map.of("credential", signGoogleToken("admin")), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void googleLogin_stillCreatesRegularUsers() throws Exception {
    var response =
        restTemplate.postForEntity(
            "/auth/google",
            Map.of("credential", signGoogleToken("aluno-sistema@ifpe.edu.br")),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    User created = userRepository.findByEmail("aluno-sistema@ifpe.edu.br").orElseThrow();
    assertThat(created.isSystemUser()).isFalse();
    assertThat(created.getPasswordHash()).isNull();
  }

  @Test
  void regularGoogleUser_cannotLoginWithPassword() {
    User user = new User();
    user.setName("Aluno");
    user.setEmail("aluno-sem-senha@ifpe.edu.br");
    user.setGoogleId("google-aluno-sem-senha");
    userRepository.save(user);

    var response = login("aluno-sem-senha@ifpe.edu.br", ADMIN_PASSWORD);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void initializer_passwordRotation_onlyNewPasswordWorks() {
    initializer("rotador", "primeira-senha-forte").run(null);
    assertThat(login("rotador", "primeira-senha-forte").getStatusCode()).isEqualTo(HttpStatus.OK);

    initializer("rotador", "segunda-senha-forte").run(null);

    assertThat(login("rotador", "primeira-senha-forte").getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(login("rotador", "segunda-senha-forte").getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void initializer_blankPassword_disablesPasswordLoginButKeepsAccount() {
    initializer("desligavel", "senha-forte-desligavel").run(null);
    assertThat(login("desligavel", "senha-forte-desligavel").getStatusCode())
        .isEqualTo(HttpStatus.OK);

    initializer("desligavel", "").run(null);

    assertThat(login("desligavel", "senha-forte-desligavel").getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    User kept = userRepository.findByEmail("desligavel").orElseThrow();
    assertThat(kept.isSystemUser()).isTrue();
    assertThat(kept.getPasswordHash()).isNull();
    assertThat(kept.isAdministrator()).isTrue();
  }

  @Test
  void initializer_shortPassword_doesNotCreateAccount() {
    initializer("senhacurta", "curta").run(null);

    assertThat(userRepository.findByEmail("senhacurta")).isEmpty();
  }

  @Test
  void initializer_doesNotTouchRegularUserWithSameIdentifier() {
    User user = new User();
    user.setName("Comum");
    user.setEmail("conflito");
    user.setGoogleId("google-conflito");
    userRepository.save(user);

    initializer("conflito", "senha-forte-conflito").run(null);

    User untouched = userRepository.findByEmail("conflito").orElseThrow();
    assertThat(untouched.isSystemUser()).isFalse();
    assertThat(untouched.getPasswordHash()).isNull();
  }

  @Test
  void database_rejectsUserWithoutGoogleIdThatIsNotSystemUser() {
    User user = new User();
    user.setName("Sem Google");
    user.setEmail("sem-google@ifpe.edu.br");

    assertThatThrownBy(() -> userRepository.save(user))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void database_rejectsSystemUserWithGoogleId() {
    User user = new User();
    user.setName("Sistema com Google");
    user.setEmail("sistema-com-google");
    user.setGoogleId("google-sistema");
    user.setSystemUser(true);

    assertThatThrownBy(() -> userRepository.save(user))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void database_rejectsGoogleUserWithPassword() {
    User user = new User();
    user.setName("Google com Senha");
    user.setEmail("google-com-senha@ifpe.edu.br");
    user.setGoogleId("google-com-senha");
    user.setPasswordHash(passwordEncoder.encode("qualquer-senha-longa"));

    assertThatThrownBy(() -> userRepository.save(user))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void database_acceptsValidSystemUser() {
    User user = new User();
    user.setName("Sistema Valido");
    user.setEmail("sistema-valido");
    user.setSystemUser(true);

    assertThat(userRepository.save(user).getId()).isNotNull();
  }
}
