package clp.inventory.permission;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.model.User;
import clp.inventory.repository.ProfileRepository;
import clp.inventory.repository.UserRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.time.Duration;
import java.time.Instant;
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
 * Confere que a migration de seed (permissões, perfis e associações sugeridos no documento de
 * proposta) rodou corretamente. O Testcontainers sobe um Postgres novo por teste, então as
 * migrations (incluindo o seed) rodam do zero a cada execução.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class SeedDataE2ETest {

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

  // /profiles e /permissions exigem ADMIN: um usuário com o perfil seedado ADMIN_ORGANIZATION.
  private long adminUserId() {
    var adminProfile =
        profileRepository.findAll().stream()
            .filter(p -> "ADMIN_ORGANIZATION".equals(p.name()))
            .findFirst()
            .orElseThrow();
    User user = new User();
    user.setName("Admin Seed Test");
    user.setEmail("admin-seed-" + System.nanoTime() + "@ifpe.edu.br");
    user.setGoogleId("google-admin-seed-" + System.nanoTime());
    user.setProfile(adminProfile);
    return userRepository.save(user).getId();
  }

  private HttpHeaders authHeaders() {
    Algorithm algorithm = Algorithm.HMAC256(TEST_SECRET);
    String token =
        JWT.create()
            .withIssuer("inventory")
            .withSubject(String.valueOf(adminUserId()))
            .withExpiresAt(Instant.now().plus(Duration.ofHours(1)))
            .sign(algorithm);

    var headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }

  private List<Map> listProfiles() {
    var response =
        restTemplate.exchange(
            "/profiles", HttpMethod.GET, new HttpEntity<>(authHeaders()), List.class);
    return response.getBody();
  }

  private Map findProfileByName(String name) {
    return listProfiles().stream()
        .filter(p -> name.equals(p.get("name")))
        .findFirst()
        .orElseThrow();
  }

  // Nomes das permissões de um perfil seedado (o ProfileDto só traz os ids).
  private List<String> permissionNamesOf(String profileName) {
    var permissions =
        restTemplate
            .exchange("/permissions", HttpMethod.GET, new HttpEntity<>(authHeaders()), List.class)
            .getBody();
    Map<Number, String> nameById = new java.util.HashMap<>();
    for (Object permission : permissions) {
      Map<?, ?> map = (Map<?, ?>) permission;
      nameById.put((Number) map.get("id"), (String) map.get("name"));
    }
    return ((List<?>) findProfileByName(profileName).get("permissionIds"))
        .stream().map(id -> nameById.get((Number) id)).toList();
  }

  @Test
  void seed_createsThreePermissions() {
    var response =
        restTemplate.exchange(
            "/permissions", HttpMethod.GET, new HttpEntity<>(authHeaders()), List.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).hasSize(3);
    assertThat(response.getBody())
        .extracting(p -> ((Map<?, ?>) p).get("name"))
        .containsExactlyInAnyOrder("ADMIN", "MANAGE_SECTOR", "MANAGE_ITEM");
  }

  @Test
  void seed_createsFourProfiles() {
    var profiles = listProfiles();

    assertThat(profiles).hasSize(4);
    assertThat(profiles)
        .extracting(p -> p.get("name"))
        .containsExactlyInAnyOrder(
            "ADMIN_ORGANIZATION", "GESTOR_SETOR", "OPERADOR_CAMPO", "CONSULTA");
  }

  @Test
  void seed_adminOrganizationHasAdminPermission() {
    assertThat(permissionNamesOf("ADMIN_ORGANIZATION")).containsExactly("ADMIN");
  }

  @Test
  void seed_gestorSetorHasManageSectorPermission() {
    assertThat(permissionNamesOf("GESTOR_SETOR")).containsExactly("MANAGE_SECTOR");
  }

  @Test
  void seed_operadorCampoHasManageItemPermission() {
    assertThat(permissionNamesOf("OPERADOR_CAMPO")).containsExactly("MANAGE_ITEM");
  }

  @Test
  void seed_consultaHasNoPermissions() {
    var consulta = findProfileByName("CONSULTA");

    assertThat((List<?>) consulta.get("permissionIds")).isEmpty();
  }
}
