package clp.inventory.auth;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.model.RevokedToken;
import clp.inventory.repository.RevokedTokenRepository;
import clp.inventory.repository.UserRepository;
import clp.inventory.service.UserService;
import clp.inventory.service.auth.SystemAdminInitializer;
import clp.inventory.testsupport.TestAccess;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sessão revogável (FIN-08): o token carrega jti, versão e emissão; o logout encerra só a sessão do
 * token; trocar a credencial ou desativar a conta encerra todas.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class SessionRevocationE2ETest {

  private static final String SECRET = "test-secret-de-pelo-menos-32-bytes-aqui";
  private static final String ADMIN_PASSWORD = "senha-forte-do-admin";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("security.token.secret", () -> SECRET);
    registry.add("system.admin.username", () -> "admin");
    registry.add("system.admin.password", () -> ADMIN_PASSWORD);
  }

  @Autowired TestRestTemplate http;
  @Autowired UserRepository users;
  @Autowired RevokedTokenRepository revokedTokens;
  @Autowired UserService userService;
  @Autowired TestAccess access;
  @Autowired PasswordEncoder passwordEncoder;

  private String login(String username, String password) {
    var response =
        http.postForEntity(
            "/auth/login", Map.of("username", username, "password", password), Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return (String) response.getBody().get("token");
  }

  private ResponseEntity<String> call(String token, HttpMethod method, String url) {
    var headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return http.exchange(url, method, new HttpEntity<>(headers), String.class);
  }

  private HttpStatus me(String token) {
    return HttpStatus.valueOf(call(token, HttpMethod.GET, "/auth/me").getStatusCode().value());
  }

  private String mint(long userId, String jti, Integer version) {
    var builder =
        JWT.create()
            .withIssuer("inventory")
            .withSubject(Long.toString(userId))
            .withExpiresAt(Instant.now().plus(Duration.ofHours(1)));
    if (jti != null) {
      builder.withJWTId(jti);
    }
    if (version != null) {
      builder.withClaim("ver", version);
    }
    return builder.sign(Algorithm.HMAC256(SECRET));
  }

  private void rotatePassword(String username, String password) {
    new SystemAdminInitializer(users, passwordEncoder, username, password).run(null);
  }

  @Test
  void token_carriesJtiVersionIssuedAtAndEightHourValidity() {
    var decoded = JWT.decode(login("admin", ADMIN_PASSWORD));

    assertThat(decoded.getId()).isNotBlank();
    assertThat(decoded.getClaim("ver").asInt()).isZero();
    assertThat(decoded.getIssuedAtAsInstant()).isNotNull();
    assertThat(Duration.between(decoded.getIssuedAtAsInstant(), decoded.getExpiresAtAsInstant()))
        .isEqualTo(Duration.ofHours(8));
  }

  @Test
  void eachLoginGetsItsOwnJti() {
    var first = JWT.decode(login("admin", ADMIN_PASSWORD)).getId();
    var second = JWT.decode(login("admin", ADMIN_PASSWORD)).getId();

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void logout_endsOnlyThatSession() {
    String thisBrowser = login("admin", ADMIN_PASSWORD);
    String otherBrowser = login("admin", ADMIN_PASSWORD);
    assertThat(me(thisBrowser)).isEqualTo(HttpStatus.OK);

    var logout = call(thisBrowser, HttpMethod.POST, "/auth/logout");

    assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    var rejected = call(thisBrowser, HttpMethod.GET, "/auth/me");
    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(rejected.getBody()).contains("Sessão encerrada");
    assertThat(me(otherBrowser)).isEqualTo(HttpStatus.OK);
  }

  @Test
  void logout_withAnAlreadyEndedToken_isRejectedLikeAnyInvalidToken() {
    String token = login("admin", ADMIN_PASSWORD);
    call(token, HttpMethod.POST, "/auth/logout");

    var again = call(token, HttpMethod.POST, "/auth/logout");

    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void logout_storesTheJtiUntilTheTokenExpires() {
    String token = login("admin", ADMIN_PASSWORD);
    var decoded = JWT.decode(token);

    call(token, HttpMethod.POST, "/auth/logout");

    var stored = revokedTokens.findById(decoded.getId()).orElseThrow();
    assertThat(stored.getExpiresAt()).isEqualTo(decoded.getExpiresAtAsInstant());
    assertThat(stored.getUserId()).isEqualTo(Long.valueOf(decoded.getSubject()));
  }

  @Test
  void logout_purgesTokensThatAlreadyExpired() {
    String stale = "jti-expirado-" + UUID.randomUUID();
    revokedTokens.save(new RevokedToken(stale, 1L, Instant.now().minus(Duration.ofMinutes(5))));
    String live = "jti-vigente-" + UUID.randomUUID();
    revokedTokens.save(new RevokedToken(live, 1L, Instant.now().plus(Duration.ofHours(1))));

    call(login("admin", ADMIN_PASSWORD), HttpMethod.POST, "/auth/logout");

    assertThat(revokedTokens.existsById(stale)).isFalse();
    assertThat(revokedTokens.existsById(live)).isTrue();
  }

  @Test
  void logout_withoutToken_isNotAccepted() {
    var response = http.postForEntity("/auth/logout", null, String.class);

    assertThat(response.getStatusCode().value()).isIn(401, 403);
  }

  @Test
  void tokenWithoutJtiOrVersion_isRejected() {
    long adminId = users.findByEmail("admin").orElseThrow().getId();

    assertThat(me(mint(adminId, UUID.randomUUID().toString(), null)))
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(me(mint(adminId, null, 0))).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(me(mint(adminId, UUID.randomUUID().toString(), 0))).isEqualTo(HttpStatus.OK);
  }

  @Test
  void tokenWithAStaleVersion_isRejected() {
    long adminId = users.findByEmail("admin").orElseThrow().getId();
    int current = users.findById(adminId).orElseThrow().getTokenVersion();

    assertThat(me(mint(adminId, UUID.randomUUID().toString(), current + 1)))
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void systemAccountPasswordChange_endsEverySessionOfTheAccount() {
    rotatePassword("rotativo", "primeira-senha-forte");
    String first = login("rotativo", "primeira-senha-forte");
    String second = login("rotativo", "primeira-senha-forte");
    assertThat(me(first)).isEqualTo(HttpStatus.OK);

    rotatePassword("rotativo", "segunda-senha-forte");

    assertThat(me(first)).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(me(second)).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(me(login("rotativo", "segunda-senha-forte"))).isEqualTo(HttpStatus.OK);
  }

  @Test
  void systemAccountStartup_withTheSamePassword_keepsSessions() {
    rotatePassword("estavel", "senha-forte-estavel");
    String token = login("estavel", "senha-forte-estavel");

    rotatePassword("estavel", "senha-forte-estavel");

    assertThat(me(token)).isEqualTo(HttpStatus.OK);
  }

  @Test
  void systemAccountPasswordLoginDisabled_endsItsSessions() {
    rotatePassword("desligavel-sessao", "senha-forte-desligavel");
    String token = login("desligavel-sessao", "senha-forte-desligavel");

    rotatePassword("desligavel-sessao", "");

    assertThat(me(token)).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void deactivatingAnAccount_doesNotLetOldTokensComeBackWhenItIsReactivated() {
    long adminId = access.admin("gestor-sessao@ifpe.edu.br").getId();
    long targetId = access.user("alvo-sessao@ifpe.edu.br", TestAccess.CONSULT).getId();
    String before = mint(targetId, UUID.randomUUID().toString(), 0);
    assertThat(me(before)).isEqualTo(HttpStatus.OK);

    userService.setActive(targetId, false, adminId);
    var deactivated = call(before, HttpMethod.GET, "/auth/me");
    assertThat(deactivated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(deactivated.getBody()).contains("Conta desativada");

    userService.setActive(targetId, true, adminId);

    assertThat(me(before)).isEqualTo(HttpStatus.UNAUTHORIZED);
    int version = users.findById(targetId).orElseThrow().getTokenVersion();
    assertThat(me(mint(targetId, UUID.randomUUID().toString(), version))).isEqualTo(HttpStatus.OK);
  }
}
