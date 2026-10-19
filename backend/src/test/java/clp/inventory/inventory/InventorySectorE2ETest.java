package clp.inventory.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.repository.SectorAllocationRepository;
import clp.inventory.repository.SectorRepository;
import clp.inventory.repository.UserRepository;
import clp.inventory.testsupport.TestAccess;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
 * Sobe a aplicação inteira (Postgres real via Testcontainers, HTTP real via TestRestTemplate) para
 * cobrir a listagem de inventários por setor: GET /inventory/sector-inventories.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class InventorySectorE2ETest {

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

  @Autowired SectorAllocationRepository sectorAllocationRepository;

  private long createUserWithProfile(String email, String roleName) {
    return access.user(email, roleName).getId();
  }

  private void allocate(long userId, long sectorId) {
    access.allocate(userId, sectorId);
  }

  private HttpHeaders authHeaders(long userId) {
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(
        JWT.create()
            .withIssuer("inventory")
            .withSubject(String.valueOf(userId))
            .withExpiresAt(Instant.now().plus(Duration.ofHours(1)))
            .sign(Algorithm.HMAC256(TEST_SECRET)));
    return headers;
  }

  private long adminUserId() {
    return createUserWithProfile("admin-" + System.nanoTime() + "@ifpe.edu.br", TestAccess.ADMIN);
  }

  private long createSector(long adminId, String name) {
    var body = new HashMap<String, Object>();
    body.put("name", name);
    var response =
        restTemplate.postForEntity(
            "/sectors", new HttpEntity<>(body, authHeaders(adminId)), Map.class);
    return ((Number) response.getBody().get("id")).longValue();
  }

  private void createInventory(long adminId, String name, long sectorId) {
    var body = new HashMap<String, Object>();
    body.put("name", name);
    body.put("description", "Descricao de teste");
    body.put("plannedEndDate", LocalDate.now().plusDays(30).toString());
    body.put("sectorId", sectorId);
    var response =
        restTemplate.postForEntity(
            "/inventory/new-inventory", new HttpEntity<>(body, authHeaders(adminId)), Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  private <T> ResponseEntity<T> listBySector(long userId, long sectorId, Class<T> type) {
    return restTemplate.exchange(
        "/inventory/sector-inventories?sectorId=" + sectorId,
        HttpMethod.GET,
        new HttpEntity<>(authHeaders(userId)),
        type);
  }

  @Test
  void sectorInventories_returnsOnlyThatSectorInventories() {
    long adminId = adminUserId();
    long sectorA = createSector(adminId, "Setor Listagem A " + System.nanoTime());
    long sectorB = createSector(adminId, "Setor Listagem B " + System.nanoTime());
    createInventory(adminId, "Inventario Setor A", sectorA);
    createInventory(adminId, "Inventario Setor B", sectorB);

    var response = listBySector(adminId, sectorA, List.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).hasSize(1);
    assertThat(((Map<?, ?>) response.getBody().get(0)).get("sectorId")).isEqualTo((int) sectorA);
  }

  @Test
  void sectorInventories_allocatedUserCanList() {
    long adminId = adminUserId();
    long sectorId = createSector(adminId, "Setor Alocado " + System.nanoTime());
    createInventory(adminId, "Inventario Alocado", sectorId);
    long manager =
        createUserWithProfile("gestor-" + System.nanoTime() + "@ifpe.edu.br", TestAccess.MANAGER);
    allocate(manager, sectorId);

    var response = listBySector(manager, sectorId, List.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).hasSize(1);
  }

  @Test
  void sectorInventories_userOutsideTheSector_returns403() {
    long adminId = adminUserId();
    long sectorId = createSector(adminId, "Setor Alheio " + System.nanoTime());
    long outsider =
        createUserWithProfile("gestor-" + System.nanoTime() + "@ifpe.edu.br", TestAccess.MANAGER);

    var response = listBySector(outsider, sectorId, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void sectorInventories_missingSectorId_returns400() {
    long adminId = adminUserId();

    var response =
        restTemplate.exchange(
            "/inventory/sector-inventories",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(adminId)),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
