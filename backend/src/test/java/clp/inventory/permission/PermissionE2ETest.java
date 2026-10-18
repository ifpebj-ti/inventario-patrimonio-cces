package clp.inventory.permission;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.model.User;
import clp.inventory.repository.ProfileRepository;
import clp.inventory.repository.UserRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
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
 * Permissões são somente leitura pela API e exigem ADMIN: não há criar, editar nem excluir (uma
 * permissão nova entra por migration). O JWT é assinado direto no teste, mesma abordagem dos outros
 * E2E.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class PermissionE2ETest {

  private static final String TEST_SECRET = "test-secret-de-pelo-menos-32-bytes-aqui";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void authProperties(DynamicPropertyRegistry registry) {
    registry.add("security.token.secret", () -> TEST_SECRET);
  }

  @Autowired TestRestTemplate restTemplate;

  @Autowired UserRepository userRepository;

  @Autowired ProfileRepository profileRepository;

  private long createUserWithProfile(String profileName) {
    var profile =
        profileRepository.findAll().stream()
            .filter(p -> profileName.equals(p.name()))
            .findFirst()
            .orElseThrow();
    User user = new User();
    user.setName("Test User");
    user.setEmail(profileName.toLowerCase() + "-" + System.nanoTime() + "@ifpe.edu.br");
    user.setGoogleId("google-" + profileName + "-" + System.nanoTime());
    user.setProfile(profile);
    return userRepository.save(user).getId();
  }

  private HttpHeaders headersFor(long userId) {
    Algorithm algorithm = Algorithm.HMAC256(TEST_SECRET);
    String token =
        JWT.create()
            .withIssuer("inventory")
            .withSubject(String.valueOf(userId))
            .withExpiresAt(Instant.now().plus(Duration.ofHours(1)))
            .sign(algorithm);

    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(token);
    return headers;
  }

  private HttpHeaders adminHeaders() {
    return headersFor(createUserWithProfile("ADMIN_ORGANIZATION"));
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> listAsAdmin() {
    var response =
        restTemplate.exchange(
            "/permissions", HttpMethod.GET, new HttpEntity<>(adminHeaders()), List.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return (List<Map<String, Object>>) response.getBody();
  }

  @Test
  void list_asAdmin_returnsTheThreeSeededPermissions() {
    var permissions = listAsAdmin();

    assertThat(permissions)
        .extracting(p -> p.get("name"))
        .containsExactlyInAnyOrder("ADMIN", "MANAGE_SECTOR", "MANAGE_ITEM");
  }

  @Test
  void getById_asAdmin_returnsPermission() {
    var seeded = listAsAdmin().get(0);
    long id = ((Number) seeded.get("id")).longValue();

    var response =
        restTemplate.exchange(
            "/permissions/" + id, HttpMethod.GET, new HttpEntity<>(adminHeaders()), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("name")).isEqualTo(seeded.get("name"));
  }

  @Test
  void getById_notFound_returns404() {
    var response =
        restTemplate.exchange(
            "/permissions/999999", HttpMethod.GET, new HttpEntity<>(adminHeaders()), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void list_asConsultaOrOperator_returns403() {
    for (String profileName : List.of("CONSULTA", "OPERADOR_CAMPO")) {
      var response =
          restTemplate.exchange(
              "/permissions",
              HttpMethod.GET,
              new HttpEntity<>(headersFor(createUserWithProfile(profileName))),
              Map.class);

      assertThat(response.getStatusCode()).as(profileName).isEqualTo(HttpStatus.FORBIDDEN);
    }
  }

  @Test
  void list_asManager_isReadOnlyAndAllowed() {
    var response =
        restTemplate.exchange(
            "/permissions",
            HttpMethod.GET,
            new HttpEntity<>(headersFor(createUserWithProfile("GESTOR_SETOR"))),
            Map[].class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void getById_asNonAdmin_returns403() {
    long consultaId = createUserWithProfile("CONSULTA");

    var response =
        restTemplate.exchange(
            "/permissions/1", HttpMethod.GET, new HttpEntity<>(headersFor(consultaId)), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void create_isNotAvailable() {
    var body = new HashMap<String, Object>();
    body.put("name", "NOVA_PERMISSAO");

    var response =
        restTemplate.postForEntity(
            "/permissions", new HttpEntity<>(body, adminHeaders()), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(listAsAdmin()).hasSize(3);
  }

  @Test
  void update_isNotAvailable_andAdminPermissionCannotBeRenamed() {
    var admin =
        listAsAdmin().stream().filter(p -> "ADMIN".equals(p.get("name"))).findFirst().orElseThrow();
    long id = ((Number) admin.get("id")).longValue();

    var response =
        restTemplate.exchange(
            "/permissions/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(Map.of("name", "OUTRO_NOME"), adminHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(listAsAdmin()).extracting(p -> p.get("name")).contains("ADMIN");
  }

  @Test
  void delete_isNotAvailable_andAdminPermissionCannotBeDeleted() {
    var admin =
        listAsAdmin().stream().filter(p -> "ADMIN".equals(p.get("name"))).findFirst().orElseThrow();
    long id = ((Number) admin.get("id")).longValue();

    var response =
        restTemplate.exchange(
            "/permissions/" + id, HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(listAsAdmin()).hasSize(3);
  }

  @Test
  void unauthenticated_returns403() {
    var response = restTemplate.getForEntity("/permissions", Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
