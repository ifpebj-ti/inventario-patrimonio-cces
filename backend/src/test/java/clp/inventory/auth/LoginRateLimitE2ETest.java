package clp.inventory.auth;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.service.auth.LoginAttemptLimiter;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Limite de tentativas do POST /auth/login. Fica numa classe própria, com limites pequenos e
 * contexto próprio, para os bloqueios não contaminarem o SystemAdminLoginE2ETest (todos os testes
 * chegam do mesmo IP, 127.0.0.1).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class LoginRateLimitE2ETest {

  private static final String ADMIN_PASSWORD = "senha-forte-do-admin";
  private static final int USER_MAX = 3;
  private static final int IP_MAX = 5;

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("security.token.secret", () -> "test-secret-de-pelo-menos-32-bytes-aqui");
    registry.add("system.admin.username", () -> "admin");
    registry.add("system.admin.password", () -> ADMIN_PASSWORD);
    registry.add("auth.login.user-max-failures", () -> String.valueOf(USER_MAX));
    registry.add("auth.login.ip-max-failures", () -> String.valueOf(IP_MAX));
  }

  @Autowired TestRestTemplate restTemplate;
  @Autowired LoginAttemptLimiter limiter;

  @BeforeEach
  void resetCounters() {
    limiter.clear();
  }

  private ResponseEntity<String> login(String username, String password) {
    return restTemplate.postForEntity(
        "/auth/login", Map.of("username", username, "password", password), String.class);
  }

  private void failLogins(String username, int times) {
    for (int i = 0; i < times; i++) {
      assertThat(login(username, "senha-errada").getStatusCode())
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  @Test
  void afterTooManyFailures_returns429WithRetryAfter_evenWithTheRightPassword() {
    failLogins("admin", USER_MAX);

    var response = login("admin", ADMIN_PASSWORD);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(response.getBody()).isEqualTo("Too many attempts");
    long retryAfter = Long.parseLong(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    assertThat(retryAfter).isBetween(1L, 15L * 60);
  }

  @Test
  void belowTheLimit_theRightPasswordStillWorks() {
    failLogins("admin", USER_MAX - 1);

    assertThat(login("admin", ADMIN_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void success_resetsTheUserCounter() {
    failLogins("admin", USER_MAX - 1);
    assertThat(login("admin", ADMIN_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);

    failLogins("admin", USER_MAX - 1);

    assertThat(login("admin", ADMIN_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void unknownUser_countsAndIsBlockedLikeAnyOther() {
    failLogins("fantasma", USER_MAX);

    var response = login("fantasma", "qualquer-senha");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(response.getBody()).isEqualTo("Too many attempts");
  }

  @Test
  void blockedUser_doesNotBlockOtherUsersBelowTheIpLimit() {
    failLogins("admin", USER_MAX);

    assertThat(login("outro-usuario", "senha-errada").getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void ipLimit_blocksEveryUserFromThatIp() {
    for (int i = 0; i < IP_MAX; i++) {
      failLogins("usuario-" + i, 1);
    }

    var response = login("admin", ADMIN_PASSWORD);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void missingCredentials_returns400AndDoesNotCountAsFailure() {
    for (int i = 0; i < USER_MAX + IP_MAX; i++) {
      var response =
          restTemplate.postForEntity(
              "/auth/login", Map.of("username", "admin", "password", ""), String.class);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    assertThat(login("admin", ADMIN_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void preflight_isNeverBlocked_andExposesRetryAfter() {
    failLogins("admin", USER_MAX);
    var headers = new HttpHeaders();
    headers.setOrigin("http://localhost:3000");
    headers.set(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");

    var preflight =
        restTemplate.exchange(
            "/auth/login", HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);

    assertThat(preflight.getStatusCode()).isEqualTo(HttpStatus.OK);

    // O 429 de verdade carrega CORS e deixa o navegador ler o Retry-After.
    var blockedHeaders = new HttpHeaders();
    blockedHeaders.setOrigin("http://localhost:3000");
    blockedHeaders.setContentType(MediaType.APPLICATION_JSON);
    var blocked =
        restTemplate.postForEntity(
            "/auth/login",
            new HttpEntity<>(Map.of("username", "admin", "password", "x"), blockedHeaders),
            String.class);

    assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(blocked.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
        .contains("Retry-After");
  }
}
