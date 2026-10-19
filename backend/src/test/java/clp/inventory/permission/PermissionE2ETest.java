package clp.inventory.permission;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.model.Sector;
import clp.inventory.model.User;
import clp.inventory.repository.SectorRepository;
import clp.inventory.repository.UserRepository;
import clp.inventory.testsupport.TestAccess;
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

  @Autowired TestAccess access;

  @Autowired SectorRepository sectorRepository;

  // Administrador, ou usuário com o cargo informado em um setor (o cargo só vale alocado).
  private long createUserWithProfile(String roleName) {
    User user = access.user("u-" + System.nanoTime() + "@ifpe.edu.br", roleName);
    if (!TestAccess.ADMIN.equals(roleName)) {
      access.allocate(
          user, sectorRepository.save(new Sector("Setor perm " + System.nanoTime(), null)));
    }
    return user.getId();
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
    return headersFor(createUserWithProfile(TestAccess.ADMIN));
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
  void list_asAdmin_returnsTheTwoSeededPermissions() {
    var permissions = listAsAdmin();

    assertThat(permissions)
        .extracting(p -> p.get("name"))
        .containsExactlyInAnyOrder("MANAGE_SECTOR", "MANAGE_ITEM");
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
    for (String profileName : List.of(TestAccess.CONSULT, TestAccess.OPERATOR)) {
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
            new HttpEntity<>(headersFor(createUserWithProfile(TestAccess.MANAGER))),
            Map[].class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void list_asManagerRoleWithoutAnyAllocation_returns403() {
    User unallocated = access.user("u-" + System.nanoTime() + "@ifpe.edu.br", TestAccess.MANAGER);

    var response =
        restTemplate.exchange(
            "/permissions",
            HttpMethod.GET,
            new HttpEntity<>(headersFor(unallocated.getId())),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void getById_asNonAdmin_returns403() {
    long consultaId = createUserWithProfile(TestAccess.CONSULT);

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
    assertThat(listAsAdmin()).hasSize(2);
  }

  @Test
  void update_isNotAvailable_andPermissionCannotBeRenamed() {
    var admin =
        listAsAdmin().stream()
            .filter(p -> "MANAGE_SECTOR".equals(p.get("name")))
            .findFirst()
            .orElseThrow();
    long id = ((Number) admin.get("id")).longValue();

    var response =
        restTemplate.exchange(
            "/permissions/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(Map.of("name", "OUTRO_NOME"), adminHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(listAsAdmin()).extracting(p -> p.get("name")).contains("MANAGE_SECTOR");
  }

  @Test
  void delete_isNotAvailable_andPermissionCannotBeDeleted() {
    var admin =
        listAsAdmin().stream()
            .filter(p -> "MANAGE_SECTOR".equals(p.get("name")))
            .findFirst()
            .orElseThrow();
    long id = ((Number) admin.get("id")).longValue();

    var response =
        restTemplate.exchange(
            "/permissions/" + id, HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(listAsAdmin()).hasSize(2);
  }

  @Test
  void unauthenticated_returns403() {
    var response = restTemplate.getForEntity("/permissions", Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
