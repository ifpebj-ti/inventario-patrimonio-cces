package clp.inventory.itemaudit;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.model.Inventory;
import clp.inventory.model.Item;
import clp.inventory.model.Sector;
import clp.inventory.model.SectorAllocation;
import clp.inventory.model.User;
import clp.inventory.repository.InventoryRepository;
import clp.inventory.repository.ProfileRepository;
import clp.inventory.repository.SectorAllocationRepository;
import clp.inventory.repository.SectorRepository;
import clp.inventory.repository.UserRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
 * cobrir a validação de item com auditoria: PATCH /item/{id}/validate e GET /item-audits.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ItemValidationE2ETest {

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

  @Autowired InventoryRepository inventoryRepository;

  private User user(String profileName) {
    var profile =
        profileRepository.findAll().stream()
            .filter(p -> profileName.equals(p.name()))
            .findFirst()
            .orElseThrow();
    User user = new User();
    user.setName("Test User");
    String email = profileName.toLowerCase() + "-" + System.nanoTime() + "@ifpe.edu.br";
    user.setEmail(email);
    user.setGoogleId("google-" + email);
    user.setProfile(profile);
    return userRepository.save(user);
  }

  private Sector sector() {
    return sectorRepository.save(new Sector("Setor " + System.nanoTime(), null));
  }

  private void allocate(User user, Sector sector) {
    sectorAllocationRepository.save(new SectorAllocation(user, sector));
  }

  private Item inventoryWithItem(User owner, Sector sector, String code) {
    Inventory inventory =
        new Inventory(
            "Inventario " + System.nanoTime(),
            "Descricao",
            LocalDate.now().plusDays(30),
            owner,
            sector);
    Item item = new Item(code, "", "Descricao do item", 1000, "Sala 1", "Responsavel");
    inventory.addItem(item);
    return inventoryRepository.save(inventory).items().get(0);
  }

  private HttpHeaders authHeaders(User user) {
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(
        JWT.create()
            .withIssuer("inventory")
            .withSubject(String.valueOf(user.getId()))
            .withExpiresAt(Instant.now().plus(Duration.ofHours(1)))
            .sign(Algorithm.HMAC256(TEST_SECRET)));
    return headers;
  }

  private <T> ResponseEntity<T> call(
      User user, HttpMethod method, String path, Class<T> responseType) {
    return restTemplate.exchange(path, method, new HttpEntity<>(authHeaders(user)), responseType);
  }

  private ResponseEntity<Map> validate(User user, long itemId) {
    return call(user, HttpMethod.PATCH, "/item/" + itemId + "/validate", Map.class);
  }

  private ResponseEntity<List> audits(User user, String query) {
    return call(user, HttpMethod.GET, "/item-audits" + query, List.class);
  }

  // Respostas de erro vêm como objeto JSON; aqui só o status interessa.
  private HttpStatusCode auditsStatus(User user, String query) {
    return call(user, HttpMethod.GET, "/item-audits" + query, String.class).getStatusCode();
  }

  @Test
  void validate_marksItemAndRecordsAudit() {
    User admin = user("ADMIN_ORGANIZATION");
    Sector sector = sector();
    User operator = user("OPERADOR_CAMPO");
    allocate(operator, sector);
    Item item = inventoryWithItem(admin, sector, "ITEM-VALIDAR");

    var response = validate(operator, item.id());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("isValid")).isEqualTo(true);
    assertThat(response.getBody().get("validatedAt")).isNotNull();

    var list = audits(operator, "?itemId=" + item.id());
    assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(list.getBody()).hasSize(1);
    var audit = (Map<?, ?>) list.getBody().get(0);
    assertThat(audit.get("eventType")).isEqualTo("VALIDATION");
    assertThat(audit.get("status")).isEqualTo("VALIDATED");
    assertThat(audit.get("targetSectorId")).isEqualTo((int) sector.id());
    assertThat(audit.get("actorSectorId")).isEqualTo((int) sector.id());
  }

  @Test
  void validate_twice_doesNotRecordASecondAudit() {
    User admin = user("ADMIN_ORGANIZATION");
    Sector sector = sector();
    Item item = inventoryWithItem(admin, sector, "ITEM-DUAS-VEZES");

    assertThat(validate(admin, item.id()).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(validate(admin, item.id()).getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(audits(admin, "?itemId=" + item.id()).getBody()).hasSize(1);
  }

  @Test
  void validate_adminOutsideTheSector_isNotPartial() {
    User admin = user("ADMIN_ORGANIZATION");
    Item item = inventoryWithItem(admin, sector(), "ITEM-ADMIN");

    validate(admin, item.id());

    var audit = (Map<?, ?>) audits(admin, "?itemId=" + item.id()).getBody().get(0);
    assertThat(audit.get("eventType")).isEqualTo("VALIDATION");
    assertThat(audit.get("actorSectorId")).isNull();
  }

  @Test
  void validate_userOutsideTheSector_returns403() {
    User admin = user("ADMIN_ORGANIZATION");
    Item item = inventoryWithItem(admin, sector(), "ITEM-SETOR-ALHEIO");
    User outsider = user("OPERADOR_CAMPO");
    allocate(outsider, sector());

    var response = validate(outsider, item.id());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(audits(admin, "?itemId=" + item.id()).getBody()).isEmpty();
  }

  @Test
  void validate_readOnlyProfile_returns403() {
    User admin = user("ADMIN_ORGANIZATION");
    Sector sector = sector();
    Item item = inventoryWithItem(admin, sector, "ITEM-CONSULTA");
    User reader = user("CONSULTA");
    allocate(reader, sector);

    var response = validate(reader, item.id());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void validate_unknownItem_returns404() {
    User admin = user("ADMIN_ORGANIZATION");

    var response = validate(admin, 999999L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void validate_finishedInventory_returns409() {
    User admin = user("ADMIN_ORGANIZATION");
    Item item = inventoryWithItem(admin, sector(), "ITEM-FINALIZADO");
    long inventoryId = item.inventory().id();
    assertThat(
            call(admin, HttpMethod.POST, "/inventory/" + inventoryId + "/finalize", Map.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    var response = validate(admin, item.id());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void audits_byTargetSector_returnsOnlyThatSector() {
    User admin = user("ADMIN_ORGANIZATION");
    Sector sectorA = sector();
    Sector sectorB = sector();
    Item itemA = inventoryWithItem(admin, sectorA, "ITEM-SETOR-A");
    Item itemB = inventoryWithItem(admin, sectorB, "ITEM-SETOR-B");
    validate(admin, itemA.id());
    validate(admin, itemB.id());

    var response = audits(admin, "?targetSectorId=" + sectorA.id());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).hasSize(1);
  }

  @Test
  void audits_withoutFilter_isAdminOnly() {
    User admin = user("ADMIN_ORGANIZATION");
    Sector sector = sector();
    User manager = user("GESTOR_SETOR");
    allocate(manager, sector);

    assertThat(auditsStatus(admin, "")).isEqualTo(HttpStatus.OK);
    assertThat(auditsStatus(manager, "")).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void audits_bySectorOfAnotherSector_returns403() {
    User manager = user("GESTOR_SETOR");
    allocate(manager, sector());

    assertThat(auditsStatus(manager, "?targetSectorId=" + sector().id()))
        .isEqualTo(HttpStatus.FORBIDDEN);
  }
}
