package clp.inventory.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * As métricas (Prometheus) só podem existir na porta de gerenciamento, que a VM publica apenas em
 * loopback. Na porta pública da API (a que o Nginx expõe) o Actuator inteiro some: se o {@code
 * /actuator/prometheus} respondesse ali, qualquer pessoa leria o estado interno da JVM.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "management.server.port=0",
      // O health de e-mail tentaria autenticar num SMTP real, que não existe no teste.
      "management.health.mail.enabled=false"
    })
@AutoConfigureTestRestTemplate
class ActuatorExposureE2ETest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("security.token.secret", () -> "test-secret-de-pelo-menos-32-bytes-aqui");
    registry.add("system.admin.password", () -> "");
  }

  @Autowired TestRestTemplate rest;
  @LocalServerPort int apiPort;
  @LocalManagementPort int managementPort;

  private ResponseEntity<String> get(int port, String path) {
    return rest.getForEntity("http://localhost:" + port + path, String.class);
  }

  @Test
  void managementPortIsNotThePublicApiPort() {
    assertThat(managementPort).isNotEqualTo(apiPort);
  }

  @Test
  void prometheusAnswersOnTheManagementPortWithoutAuthentication() {
    ResponseEntity<String> response = get(managementPort, "/actuator/prometheus");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("jvm_memory_used_bytes");
  }

  @Test
  void prometheusNeverAnswersOnThePublicApiPort() {
    ResponseEntity<String> response = get(apiPort, "/actuator/prometheus");

    assertThat(response.getStatusCode().is2xxSuccessful()).isFalse();
    assertThat(response.getBody() == null ? "" : response.getBody())
        .doesNotContain("jvm_memory_used_bytes");
  }

  @Test
  void healthLivesOnTheManagementPortOnly() {
    for (String path :
        new String[] {
          "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"
        }) {
      ResponseEntity<String> response = get(managementPort, path);
      assertThat(response.getStatusCode())
          .as(path + " -> " + response.getBody())
          .isEqualTo(HttpStatus.OK);
    }
    assertThat(get(apiPort, "/actuator/health").getStatusCode().is2xxSuccessful()).isFalse();
  }

  @Test
  void onlyHealthAndPrometheusAreExposedEvenOnTheManagementPort() {
    for (String endpoint : new String[] {"env", "beans", "heapdump", "threaddump", "loggers"}) {
      // 403 ou 404: o que importa é nunca responder 2xx com o conteúdo do endpoint.
      assertThat(get(managementPort, "/actuator/" + endpoint).getStatusCode().is4xxClientError())
          .as(endpoint)
          .isTrue();
    }
  }

  @Test
  void businessEndpointsStayProtectedOnThePublicPort() {
    assertThat(get(apiPort, "/sectors").getStatusCode())
        .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
  }

  @Test
  void requestMetricsUseUriTemplatesAndNeverLeakCredentials() {
    get(apiPort, "/sectors");

    String metrics = get(managementPort, "/actuator/prometheus").getBody();

    assertThat(metrics).contains("http_server_requests_seconds");
    assertThat(metrics).doesNotContain("Bearer").doesNotContain("eyJ");
  }
}
