package clp.inventory.room;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.model.Inventory;
import clp.inventory.model.Item;
import clp.inventory.model.Sector;
import clp.inventory.model.User;
import clp.inventory.repository.InventoryRepository;
import clp.inventory.repository.RoomRepository;
import clp.inventory.repository.SectorRepository;
import clp.inventory.testsupport.TestAccess;
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
 * Salas por setor: quem lê o setor lista; administrador e gestor do setor criam, renomeiam e
 * excluem; o nome é único no setor sem diferenciar caixa, acento e espaços; sala com item não sai.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class RoomE2ETest {

  private static final String TEST_SECRET = "test-secret-de-pelo-menos-32-bytes-aqui";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void authProperties(DynamicPropertyRegistry registry) {
    registry.add("security.token.secret", () -> TEST_SECRET);
  }

  @Autowired TestRestTemplate restTemplate;
  @Autowired TestAccess access;
  @Autowired SectorRepository sectorRepository;
  @Autowired InventoryRepository inventoryRepository;
  @Autowired RoomRepository roomRepository;

  private User user(String roleName) {
    return access.user("user-" + System.nanoTime() + "@ifpe.edu.br", roleName);
  }

  private Sector sector() {
    return sectorRepository.save(new Sector("Setor " + System.nanoTime(), null));
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
      User user, HttpMethod method, String path, Object body, Class<T> type) {
    return restTemplate.exchange(path, method, new HttpEntity<>(body, authHeaders(user)), type);
  }

  private ResponseEntity<Map> create(User user, long sectorId, String name) {
    return call(
        user, HttpMethod.POST, "/sectors/" + sectorId + "/rooms", Map.of("name", name), Map.class);
  }

  private HttpStatusCode createStatus(User user, long sectorId, String name) {
    return call(
            user,
            HttpMethod.POST,
            "/sectors/" + sectorId + "/rooms",
            Map.of("name", name),
            String.class)
        .getStatusCode();
  }

  private long idOf(ResponseEntity<Map> response) {
    return ((Number) response.getBody().get("id")).longValue();
  }

  @Test
  void adminAndSectorManager_createRenameAndDelete() {
    Sector sector = sector();
    User admin = user(TestAccess.ADMIN);
    User manager = user(TestAccess.MANAGER);
    access.allocate(manager, sector);

    var created = create(admin, sector.id(), "  Sala   105 ");
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().get("name")).isEqualTo("Sala 105");
    assertThat(created.getBody().get("itemCount")).isEqualTo(0);
    assertThat(created.getBody().get("deletable")).isEqualTo(true);

    var byManager = create(manager, sector.id(), "Laboratório");
    assertThat(byManager.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    var renamed =
        call(
            manager,
            HttpMethod.PUT,
            "/rooms/" + idOf(created),
            Map.of("name", "Sala 106"),
            Map.class);
    assertThat(renamed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(renamed.getBody().get("name")).isEqualTo("Sala 106");

    var deleted = call(manager, HttpMethod.DELETE, "/rooms/" + idOf(byManager), null, String.class);
    assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(roomRepository.findById(idOf(byManager))).isEmpty();
  }

  @Test
  void duplicateNameInOtherSpelling_isRejected_butAllowedInAnotherSector() {
    Sector sector = sector();
    Sector other = sector();
    User admin = user(TestAccess.ADMIN);
    assertThat(create(admin, sector.id(), "Laboratório 1").getStatusCode())
        .isEqualTo(HttpStatus.CREATED);

    assertThat(createStatus(admin, sector.id(), "  LABORATORIO   1 "))
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(create(admin, other.id(), "Laboratório 1").getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void renameToAnExistingNameIsRejected_butKeepingOwnNameWithOtherCaseIsFine() {
    Sector sector = sector();
    User admin = user(TestAccess.ADMIN);
    long first = idOf(create(admin, sector.id(), "Sala A"));
    create(admin, sector.id(), "Sala B");

    var clash =
        call(admin, HttpMethod.PUT, "/rooms/" + first, Map.of("name", "sala b"), String.class);
    assertThat(clash.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    var recase =
        call(admin, HttpMethod.PUT, "/rooms/" + first, Map.of("name", "SALA A"), Map.class);
    assertThat(recase.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(recase.getBody().get("name")).isEqualTo("SALA A");
  }

  @Test
  void invalidNames_areRejected() {
    Sector sector = sector();
    User admin = user(TestAccess.ADMIN);
    assertThat(createStatus(admin, sector.id(), "   ")).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(createStatus(admin, sector.id(), "x".repeat(101))).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(create(admin, sector.id(), "x".repeat(100)).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void operatorAndConsult_listButCannotChange() {
    Sector sector = sector();
    User admin = user(TestAccess.ADMIN);
    long roomId = idOf(create(admin, sector.id(), "Sala 1"));
    User operator = user(TestAccess.OPERATOR);
    User consult = user(TestAccess.CONSULT);
    access.allocate(operator, sector);
    access.allocate(consult, sector);

    for (User reader : List.of(operator, consult)) {
      var list =
          call(reader, HttpMethod.GET, "/sectors/" + sector.id() + "/rooms", null, List.class);
      assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(list.getBody()).hasSize(1);
      assertThat(createStatus(reader, sector.id(), "Sala 2")).isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(
              call(reader, HttpMethod.PUT, "/rooms/" + roomId, Map.of("name", "X"), String.class)
                  .getStatusCode())
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(
              call(reader, HttpMethod.DELETE, "/rooms/" + roomId, null, String.class)
                  .getStatusCode())
          .isEqualTo(HttpStatus.FORBIDDEN);
    }
  }

  @Test
  void managerOfAnotherSector_andOutsiders_haveNoAccess() {
    Sector sector = sector();
    Sector other = sector();
    User admin = user(TestAccess.ADMIN);
    long roomId = idOf(create(admin, sector.id(), "Sala 1"));
    User foreignManager = user(TestAccess.MANAGER);
    access.allocate(foreignManager, other);
    User outsider = user(TestAccess.OPERATOR);

    assertThat(createStatus(foreignManager, sector.id(), "Sala 2")).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(foreignManager, HttpMethod.DELETE, "/rooms/" + roomId, null, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(
                    foreignManager,
                    HttpMethod.GET,
                    "/sectors/" + sector.id() + "/rooms",
                    null,
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(outsider, HttpMethod.GET, "/sectors/" + sector.id() + "/rooms", null, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void unknownSectorOrRoom_is404() {
    User admin = user(TestAccess.ADMIN);
    assertThat(createStatus(admin, 999_999_999L, "Sala")).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            call(admin, HttpMethod.PUT, "/rooms/999999999", Map.of("name", "X"), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            call(admin, HttpMethod.DELETE, "/rooms/999999999", null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void roomWithItems_cannotBeDeleted_andReportsItemCount() {
    Sector sector = sector();
    User admin = user(TestAccess.ADMIN);
    var created = create(admin, sector.id(), "Sala 1");
    var room = roomRepository.findById(idOf(created)).orElseThrow();

    Inventory inventory =
        new Inventory("Inv " + System.nanoTime(), "", LocalDate.now().plusDays(30), admin, sector);
    Item item = new Item("COD-1", "", "Mesa", 1000, null, "Fulano");
    item.setRoom(room);
    inventory.addItem(item);
    inventoryRepository.save(inventory);

    var list = call(admin, HttpMethod.GET, "/sectors/" + sector.id() + "/rooms", null, List.class);
    var entry = (Map<?, ?>) list.getBody().get(0);
    assertThat(entry.get("itemCount")).isEqualTo(1);
    assertThat(entry.get("deletable")).isEqualTo(false);

    var deleted = call(admin, HttpMethod.DELETE, "/rooms/" + room.id(), null, String.class);
    assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(roomRepository.findById(room.id())).isPresent();
  }

  @Test
  void deletingAnEmptySector_removesItsRooms() {
    Sector sector = sector();
    User admin = user(TestAccess.ADMIN);
    long roomId = idOf(create(admin, sector.id(), "Sala 1"));

    var deleted = call(admin, HttpMethod.DELETE, "/sectors/" + sector.id(), null, String.class);

    assertThat(deleted.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(roomRepository.findById(roomId)).isEmpty();
  }
}
