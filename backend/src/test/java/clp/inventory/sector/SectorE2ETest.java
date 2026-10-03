package clp.inventory.sector;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.model.Sector;
import clp.inventory.model.SectorAllocation;
import clp.inventory.model.User;
import clp.inventory.repository.ProfileRepository;
import clp.inventory.repository.SectorAllocationRepository;
import clp.inventory.repository.SectorRepository;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sobe a aplicação inteira (Postgres real via Testcontainers, HTTP real via TestRestTemplate).
 * Setor agora exige permissão de verdade (ADMIN para escrita, ADMIN ou MANAGE_SECTOR para
 * leitura), então os testes precisam de usuários reais com um dos perfis seedados
 * (ADMIN_ORGANIZATION, GESTOR_SETOR, OPERADOR_CAMPO, CONSULTA) em vez de um JWT genérico.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SectorE2ETest {

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

  @Autowired SectorRepository sectorRepository;

  @Autowired SectorAllocationRepository sectorAllocationRepository;

  private void allocateSectorDirectly(long userId, long sectorId) {
    User user = userRepository.findById(userId).orElseThrow();
    Sector sector = sectorRepository.findById(sectorId).orElseThrow();
    sectorAllocationRepository.save(new SectorAllocation(user, sector));
  }

  private long createUserWithProfile(String email, String profileName) {
    var profile =
        profileRepository.findAll().stream()
            .filter(p -> profileName.equals(p.name()))
            .findFirst()
            .orElseThrow();
    User user = new User();
    user.setName("Test User");
    user.setEmail(email);
    user.setGoogleId("google-" + email);
    user.setProfile(profile);
    return userRepository.save(user).getId();
  }

  private String mintToken(long userId) {
    Algorithm algorithm = Algorithm.HMAC256(TEST_SECRET);
    return JWT.create()
        .withIssuer("inventory")
        .withSubject(String.valueOf(userId))
        .withExpiresAt(Instant.now().plus(Duration.ofHours(1)))
        .sign(algorithm);
  }

  private HttpHeaders authHeadersFor(long userId) {
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(mintToken(userId));
    return headers;
  }

  private long adminUserId() {
    return createUserWithProfile("admin-" + System.nanoTime() + "@ifpe.edu.br", "ADMIN_ORGANIZATION");
  }

  private Map<String, Object> sectorBody(String name, String code) {
    var body = new HashMap<String, Object>();
    body.put("name", name);
    body.put("code", code);
    return body;
  }

  private ResponseEntity<Map> create(String name, String code, long actingUserId) {
    return restTemplate.postForEntity(
        "/sectors", new HttpEntity<>(sectorBody(name, code), authHeadersFor(actingUserId)), Map.class);
  }

  private ResponseEntity<Map> createAsAdmin(String name, String code) {
    return create(name, code, adminUserId());
  }

  @Test
  void create_returnsCreatedSector() {
    var response = createAsAdmin("Patrimonio", "PAT");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().get("name")).isEqualTo("Patrimonio");
    assertThat(response.getBody().get("code")).isEqualTo("PAT");
    assertThat(response.getBody().get("active")).isEqualTo(true);
    assertThat(response.getBody().get("id")).isNotNull();
  }

  @Test
  void create_blankName_returns400() {
    var response = createAsAdmin("", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void create_duplicateName_returns400() {
    long adminId = adminUserId();
    create("Setor Duplicado", null, adminId);
    var second = create("Setor Duplicado", null, adminId);

    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void create_asNonAdmin_returns403() {
    long nonAdminId = createUserWithProfile("sem-admin-criar@ifpe.edu.br", "CONSULTA");

    var response = create("Setor Sem Permissao", null, nonAdminId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void list_returnsAllSectors() {
    long adminId = adminUserId();
    create("Setor Listagem 1", null, adminId);
    create("Setor Listagem 2", null, adminId);

    var response =
        restTemplate.exchange(
            "/sectors", HttpMethod.GET, new HttpEntity<>(authHeadersFor(adminId)), List.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotEmpty();
  }

  @Test
  void list_asGestorSetor_returnsAllSectors() {
    long gestorId = createUserWithProfile("gestor-listagem@ifpe.edu.br", "GESTOR_SETOR");

    var response =
        restTemplate.exchange(
            "/sectors", HttpMethod.GET, new HttpEntity<>(authHeadersFor(gestorId)), List.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void list_asUserWithoutPermission_returns403() {
    long consultaId = createUserWithProfile("consulta-listagem@ifpe.edu.br", "CONSULTA");

    var response =
        restTemplate.exchange(
            "/sectors", HttpMethod.GET, new HttpEntity<>(authHeadersFor(consultaId)), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void getById_returnsSector() {
    long adminId = adminUserId();
    var created = create("Setor Busca", null, adminId);
    long id = ((Number) created.getBody().get("id")).longValue();

    var response =
        restTemplate.exchange(
            "/sectors/" + id, HttpMethod.GET, new HttpEntity<>(authHeadersFor(adminId)), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("name")).isEqualTo("Setor Busca");
  }

  @Test
  void getById_notFound_returns404() {
    long adminId = adminUserId();

    var response =
        restTemplate.exchange(
            "/sectors/999999",
            HttpMethod.GET,
            new HttpEntity<>(authHeadersFor(adminId)),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void getById_asUserWithoutPermission_returns403() {
    long adminId = adminUserId();
    var created = create("Setor Busca Sem Permissao", null, adminId);
    long id = ((Number) created.getBody().get("id")).longValue();
    long operadorId = createUserWithProfile("operador-busca@ifpe.edu.br", "OPERADOR_CAMPO");

    var response =
        restTemplate.exchange(
            "/sectors/" + id,
            HttpMethod.GET,
            new HttpEntity<>(authHeadersFor(operadorId)),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void update_returnsUpdatedSector() {
    long adminId = adminUserId();
    var created = create("Setor Original", null, adminId);
    long id = ((Number) created.getBody().get("id")).longValue();

    var response =
        restTemplate.exchange(
            "/sectors/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(sectorBody("Setor Atualizado", "ATU"), authHeadersFor(adminId)),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("name")).isEqualTo("Setor Atualizado");
    assertThat(response.getBody().get("code")).isEqualTo("ATU");
  }

  @Test
  void update_duplicateName_returns400() {
    long adminId = adminUserId();
    create("Setor A", null, adminId);
    var setorB = create("Setor B", null, adminId);
    long idB = ((Number) setorB.getBody().get("id")).longValue();

    var response =
        restTemplate.exchange(
            "/sectors/" + idB,
            HttpMethod.PUT,
            new HttpEntity<>(sectorBody("Setor A", null), authHeadersFor(adminId)),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void update_asNonAdmin_returns403() {
    long adminId = adminUserId();
    var created = create("Setor Edicao Sem Permissao", null, adminId);
    long id = ((Number) created.getBody().get("id")).longValue();
    long nonAdminId = createUserWithProfile("sem-admin-editar@ifpe.edu.br", "GESTOR_SETOR");

    var response =
        restTemplate.exchange(
            "/sectors/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(sectorBody("Setor Editado Sem Permissao", null), authHeadersFor(nonAdminId)),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void deactivate_returnsDeactivatedSector() {
    long adminId = adminUserId();
    var created = create("Setor Para Desativar", null, adminId);
    long id = ((Number) created.getBody().get("id")).longValue();

    var response =
        restTemplate.exchange(
            "/sectors/" + id + "/deactivate",
            HttpMethod.PATCH,
            new HttpEntity<>(authHeadersFor(adminId)),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("active")).isEqualTo(false);
  }

  @Test
  void activate_returnsActivatedSector() {
    long adminId = adminUserId();
    var created = create("Setor Para Reativar", null, adminId);
    long id = ((Number) created.getBody().get("id")).longValue();
    restTemplate.exchange(
        "/sectors/" + id + "/deactivate",
        HttpMethod.PATCH,
        new HttpEntity<>(authHeadersFor(adminId)),
        Map.class);

    var response =
        restTemplate.exchange(
            "/sectors/" + id + "/activate",
            HttpMethod.PATCH,
            new HttpEntity<>(authHeadersFor(adminId)),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("active")).isEqualTo(true);
  }

  @Test
  void deactivate_notFound_returns404() {
    long adminId = adminUserId();

    var response =
        restTemplate.exchange(
            "/sectors/999999/deactivate",
            HttpMethod.PATCH,
            new HttpEntity<>(authHeadersFor(adminId)),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void activate_notFound_returns404() {
    long adminId = adminUserId();

    var response =
        restTemplate.exchange(
            "/sectors/999999/activate",
            HttpMethod.PATCH,
            new HttpEntity<>(authHeadersFor(adminId)),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void activate_asNonAdmin_returns403() {
    long adminId = adminUserId();
    var created = create("Setor Ativar Sem Permissao", null, adminId);
    long id = ((Number) created.getBody().get("id")).longValue();
    long nonAdminId = createUserWithProfile("sem-admin-ativar@ifpe.edu.br", "GESTOR_SETOR");

    var response =
        restTemplate.exchange(
            "/sectors/" + id + "/activate",
            HttpMethod.PATCH,
            new HttpEntity<>(authHeadersFor(nonAdminId)),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void deactivate_asNonAdmin_returns403() {
    long adminId = adminUserId();
    var created = create("Setor Desativar Sem Permissao", null, adminId);
    long id = ((Number) created.getBody().get("id")).longValue();
    long nonAdminId = createUserWithProfile("sem-admin-desativar@ifpe.edu.br", "GESTOR_SETOR");

    var response =
        restTemplate.exchange(
            "/sectors/" + id + "/deactivate",
            HttpMethod.PATCH,
            new HttpEntity<>(authHeadersFor(nonAdminId)),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void listUsers_returnsAllocatedUsers() {
    long adminId = adminUserId();
    var created = create("Setor Com Usuarios", null, adminId);
    long sectorId = ((Number) created.getBody().get("id")).longValue();
    long userId = createUserWithProfile("usuario-alocado@ifpe.edu.br", "CONSULTA");
    allocateSectorDirectly(userId, sectorId);

    var response =
        restTemplate.exchange(
            "/sectors/" + sectorId + "/users",
            HttpMethod.GET,
            new HttpEntity<>(authHeadersFor(adminId)),
            List.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).hasSize(1);
  }

  @Test
  void listUsers_asGestorSetor_succeeds() {
    long adminId = adminUserId();
    var created = create("Setor Usuarios Gestor", null, adminId);
    long sectorId = ((Number) created.getBody().get("id")).longValue();
    long gestorId = createUserWithProfile("gestor-usuarios@ifpe.edu.br", "GESTOR_SETOR");

    var response =
        restTemplate.exchange(
            "/sectors/" + sectorId + "/users",
            HttpMethod.GET,
            new HttpEntity<>(authHeadersFor(gestorId)),
            List.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void listUsers_asUserWithoutPermission_returns403() {
    long adminId = adminUserId();
    var created = create("Setor Usuarios Sem Permissao", null, adminId);
    long sectorId = ((Number) created.getBody().get("id")).longValue();
    long consultaId = createUserWithProfile("consulta-usuarios@ifpe.edu.br", "CONSULTA");

    var response =
        restTemplate.exchange(
            "/sectors/" + sectorId + "/users",
            HttpMethod.GET,
            new HttpEntity<>(authHeadersFor(consultaId)),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void listUsers_notFound_returns404() {
    long adminId = adminUserId();

    var response =
        restTemplate.exchange(
            "/sectors/999999/users",
            HttpMethod.GET,
            new HttpEntity<>(authHeadersFor(adminId)),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void unauthenticated_returns403() {
    // SecurityConfig ainda não tem um AuthenticationEntryPoint customizado (fica um TODO lá),
    // então o Spring Security responde 403 para requisição sem token, não 401.
    var response = restTemplate.getForEntity("/sectors", Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
