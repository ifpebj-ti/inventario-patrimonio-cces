package clp.inventory.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** CORS global: só as origens de cors.allowed-origins chamam a API pelo navegador. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class CorsE2ETest {

  private static final String ALLOWED = "https://app.example.test";
  private static final String OTHER_ALLOWED = "https://outro.example.test";
  private static final String FORBIDDEN = "https://evil.example";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("security.token.secret", () -> "test-secret-de-pelo-menos-32-bytes-aqui");
    registry.add("system.admin.password", () -> "");
    registry.add("cors.allowed-origins", () -> ALLOWED + ", " + OTHER_ALLOWED);
  }

  @Autowired TestRestTemplate restTemplate;

  private HttpHeaders preflight(String origin, String method, String requestHeaders) {
    var headers = new HttpHeaders();
    headers.setOrigin(origin);
    headers.set(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method);
    if (requestHeaders != null) {
      headers.set(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, requestHeaders);
    }
    return headers;
  }

  @Test
  void preflight_fromAllowedOrigin_isAcceptedForAuthenticatedEndpoints() {
    var response =
        restTemplate.exchange(
            "/inventory/all",
            HttpMethod.OPTIONS,
            new HttpEntity<>(preflight(ALLOWED, "GET", "authorization,content-type")),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo(ALLOWED);
    assertThat(response.getHeaders().getAccessControlAllowMethods()).contains(HttpMethod.GET);
    assertThat(response.getHeaders().getAccessControlAllowCredentials()).isFalse();
  }

  @Test
  void preflight_fromEachConfiguredOrigin_isAccepted() {
    var response =
        restTemplate.exchange(
            "/auth/login",
            HttpMethod.OPTIONS,
            new HttpEntity<>(preflight(OTHER_ALLOWED, "POST", "content-type")),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo(OTHER_ALLOWED);
  }

  @Test
  void preflight_fromUnknownOrigin_isRejectedWithoutCorsHeaders() {
    var response =
        restTemplate.exchange(
            "/auth/login",
            HttpMethod.OPTIONS,
            new HttpEntity<>(preflight(FORBIDDEN, "POST", "content-type")),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
  }

  @Test
  void preflight_withUnlistedRequestHeader_isRejected() {
    var response =
        restTemplate.exchange(
            "/auth/login",
            HttpMethod.OPTIONS,
            new HttpEntity<>(preflight(ALLOWED, "POST", "x-internal-debug")),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
  }

  @Test
  void actualRequest_fromAllowedOrigin_carriesCorsHeaders() {
    var headers = new HttpHeaders();
    headers.setOrigin(ALLOWED);
    headers.setContentType(MediaType.APPLICATION_JSON);

    var response =
        restTemplate.postForEntity(
            "/auth/login",
            new HttpEntity<>(Map.of("username", "admin", "password", "senha-errada"), headers),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo(ALLOWED);
    assertThat(response.getHeaders().getAccessControlExposeHeaders()).contains("Retry-After");
  }

  @Test
  void actualRequest_fromUnknownOrigin_isRejected() {
    var headers = new HttpHeaders();
    headers.setOrigin(FORBIDDEN);
    headers.setContentType(MediaType.APPLICATION_JSON);

    var response =
        restTemplate.postForEntity(
            "/auth/login",
            new HttpEntity<>(Map.of("username", "admin", "password", "senha-errada"), headers),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
  }

  @Test
  void requestWithoutOrigin_isNotAffectedByCors() {
    var response =
        restTemplate.postForEntity(
            "/auth/login", Map.of("username", "admin", "password", "senha-errada"), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
  }
}
