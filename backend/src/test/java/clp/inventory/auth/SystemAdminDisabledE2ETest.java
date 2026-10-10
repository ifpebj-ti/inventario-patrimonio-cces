package clp.inventory.auth;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Sem SYSTEM_ADMIN_PASSWORD a conta de sistema não existe e o login por senha fica desligado. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class SystemAdminDisabledE2ETest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("security.token.secret", () -> "test-secret-de-pelo-menos-32-bytes-aqui");
    registry.add("system.admin.password", () -> "");
  }

  @Autowired TestRestTemplate restTemplate;
  @Autowired UserRepository userRepository;

  @Test
  void login_withoutConfiguredPassword_returns401AndNoSystemAccountExists() {
    var response =
        restTemplate.postForEntity(
            "/auth/login",
            Map.of("username", "admin", "password", "qualquer-senha-longa"),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(userRepository.findByEmail("admin")).isEmpty();
  }
}
