package clp.inventory.room;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.model.Inventory;
import clp.inventory.model.Item;
import clp.inventory.model.Room;
import clp.inventory.model.Sector;
import clp.inventory.model.User;
import clp.inventory.repository.InventoryRepository;
import clp.inventory.repository.ItemRepository;
import clp.inventory.repository.RoomRepository;
import clp.inventory.repository.SectorRepository;
import clp.inventory.testsupport.TestAccess;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Validar item informando a sala: em setor com salas ela é obrigatória (400), vem da sala do setor,
 * e a divergência entre a cadastrada e a encontrada fica no item e na auditoria. Também cobre a
 * importação do CSV (salas novas pré-validadas e criadas) e o cadastro avulso.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ItemRoomValidationE2ETest {

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
  @Autowired ItemRepository itemRepository;
  @Autowired RoomRepository roomRepository;

  private User user(String roleName) {
    return access.user("user-" + System.nanoTime() + "@ifpe.edu.br", roleName);
  }

  private Sector sector() {
    return sectorRepository.save(new Sector("Setor " + System.nanoTime(), null));
  }

  private Room room(Sector sector, String name) {
    return roomRepository.save(
        new Room(sector, name, clp.inventory.utils.RoomNames.normalize(name)));
  }

  private Inventory inventory(User owner, Sector sector) {
    return inventoryRepository.save(
        new Inventory("Inv " + System.nanoTime(), "", LocalDate.now().plusDays(30), owner, sector));
  }

  /** Item cadastrado na sala informada (nula = sem sala cadastrada). */
  private Item item(Inventory inventory, String code, Room registeredRoom) {
    Item item = new Item(code, "", "Descricao", 1000, null, "Responsavel");
    item.setRoom(registeredRoom);
    inventory.addItem(item);
    Inventory saved = inventoryRepository.save(inventory);
    return saved.items().stream().filter(i -> i.code().equals(code)).findFirst().orElseThrow();
  }

  private HttpHeaders authHeaders(User user, MediaType contentType) {
    var headers = new HttpHeaders();
    headers.setContentType(contentType);
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
    return restTemplate.exchange(
        path, method, new HttpEntity<>(body, authHeaders(user, MediaType.APPLICATION_JSON)), type);
  }

  private ResponseEntity<Map> validate(User user, long itemId, Long roomId) {
    Map<String, Object> body = roomId == null ? null : Map.of("roomId", roomId);
    return call(user, HttpMethod.PATCH, "/item/" + itemId + "/validate", body, Map.class);
  }

  private HttpStatusCode validateStatus(User user, long itemId, Long roomId) {
    Map<String, Object> body = roomId == null ? null : Map.of("roomId", roomId);
    return call(user, HttpMethod.PATCH, "/item/" + itemId + "/validate", body, String.class)
        .getStatusCode();
  }

  private List<Map<?, ?>> audits(User user, long itemId) {
    var response = call(user, HttpMethod.GET, "/item-audits?itemId=" + itemId, null, List.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    List<Map<?, ?>> list = response.getBody();
    return list;
  }

  private ResponseEntity<String> uploadCsv(
      User user, String path, Map<String, String> fields, String csv) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    fields.forEach(form::add);
    form.add(
        "file",
        new ByteArrayResource(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
          @Override
          public String getFilename() {
            return "itens.csv";
          }
        });
    return restTemplate.exchange(
        path,
        HttpMethod.POST,
        new HttpEntity<>(form, authHeaders(user, MediaType.MULTIPART_FORM_DATA)),
        String.class);
  }

  private static final String CSV_HEADER = "Código;Descrição;Responsável;Valor;Sala;Observações\n";

  @Test
  void sectorWithRooms_requiresTheRoomToValidate() {
    Sector sector = sector();
    User operator = user(TestAccess.OPERATOR);
    access.allocate(operator, sector);
    Room r105 = room(sector, "Sala 105");
    Item item = item(inventory(user(TestAccess.ADMIN), sector), "I-1", r105);

    assertThat(validateStatus(operator, item.id(), null)).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(itemRepository.findById(item.id()).orElseThrow().isValid()).isFalse();
    assertThat(audits(operator, item.id())).isEmpty();
  }

  @Test
  void roomOfAnotherSector_isRejected() {
    Sector sector = sector();
    Sector other = sector();
    User operator = user(TestAccess.OPERATOR);
    access.allocate(operator, sector);
    Room mine = room(sector, "Sala 105");
    Room foreign = room(other, "Sala 105");
    Item item = item(inventory(user(TestAccess.ADMIN), sector), "I-1", mine);

    assertThat(validateStatus(operator, item.id(), foreign.id())).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(validateStatus(operator, item.id(), 999_999_999L)).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(itemRepository.findById(item.id()).orElseThrow().isValid()).isFalse();
  }

  @Test
  void confirmingTheRegisteredRoom_validatesWithoutDivergence() {
    Sector sector = sector();
    User operator = user(TestAccess.OPERATOR);
    access.allocate(operator, sector);
    Room r105 = room(sector, "Sala 105");
    Item item = item(inventory(user(TestAccess.ADMIN), sector), "I-1", r105);

    var response = validate(operator, item.id(), r105.id());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("isValid")).isEqualTo(true);
    assertThat(response.getBody().get("roomName")).isEqualTo("Sala 105");
    assertThat(response.getBody().get("foundRoomName")).isEqualTo("Sala 105");
    assertThat(response.getBody().get("divergent")).isEqualTo(false);
    var audit = audits(operator, item.id()).get(0);
    assertThat(audit.get("eventType")).isEqualTo("VALIDATION");
    assertThat(audit.get("divergent")).isEqualTo(false);
    assertThat(((Number) audit.get("foundRoomId")).longValue()).isEqualTo(r105.id());
    assertThat(((Number) audit.get("expectedRoomId")).longValue()).isEqualTo(r105.id());
  }

  @Test
  void anotherRoom_isDivergent_andDoesNotChangeTheRegisteredRoom() {
    Sector sector = sector();
    User operator = user(TestAccess.OPERATOR);
    access.allocate(operator, sector);
    Room r105 = room(sector, "Sala 105");
    Room r203 = room(sector, "Sala 203");
    Item item = item(inventory(user(TestAccess.ADMIN), sector), "I-1", r105);

    var response = validate(operator, item.id(), r203.id());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("divergent")).isEqualTo(true);
    assertThat(response.getBody().get("roomName")).isEqualTo("Sala 105");
    assertThat(response.getBody().get("foundRoomName")).isEqualTo("Sala 203");
    assertThat(response.getBody().get("locale")).isEqualTo("Sala 105");
    var audit = audits(operator, item.id()).get(0);
    assertThat(audit.get("divergent")).isEqualTo(true);
    assertThat(audit.get("foundRoomName")).isEqualTo("Sala 203");
    assertThat(audit.get("expectedRoomName")).isEqualTo("Sala 105");
  }

  @Test
  void itemWithoutRegisteredRoom_needsTheRoom_andIsNeverDivergent() {
    Sector sector = sector();
    User operator = user(TestAccess.OPERATOR);
    access.allocate(operator, sector);
    Room r105 = room(sector, "Sala 105");
    Item item = item(inventory(user(TestAccess.ADMIN), sector), "I-1", null);

    assertThat(validateStatus(operator, item.id(), null)).isEqualTo(HttpStatus.BAD_REQUEST);
    var response = validate(operator, item.id(), r105.id());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("foundRoomName")).isEqualTo("Sala 105");
    assertThat(response.getBody().get("divergent")).isEqualTo(false);
  }

  @Test
  void sectorWithoutRooms_validatesAsBefore() {
    Sector sector = sector();
    User operator = user(TestAccess.OPERATOR);
    access.allocate(operator, sector);
    Item item = item(inventory(user(TestAccess.ADMIN), sector), "I-1", null);

    var response = validate(operator, item.id(), null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("isValid")).isEqualTo(true);
    assertThat(response.getBody().get("foundRoomId")).isNull();
    assertThat(audits(operator, item.id())).hasSize(1);
  }

  @Test
  void validatingAgainWithTheSameRoomIsIdempotent_butAnotherRoomIsRecorded() {
    Sector sector = sector();
    User operator = user(TestAccess.OPERATOR);
    access.allocate(operator, sector);
    Room r105 = room(sector, "Sala 105");
    Room r203 = room(sector, "Sala 203");
    Item item = item(inventory(user(TestAccess.ADMIN), sector), "I-1", r105);

    validate(operator, item.id(), r105.id());
    validate(operator, item.id(), r105.id());
    assertThat(audits(operator, item.id())).hasSize(1);

    var moved = validate(operator, item.id(), r203.id());
    assertThat(moved.getBody().get("divergent")).isEqualTo(true);
    assertThat(audits(operator, item.id())).hasSize(2);
  }

  @Test
  void putThatMarksAsValid_followsTheSameRuleAndAudit() {
    Sector sector = sector();
    User operator = user(TestAccess.OPERATOR);
    access.allocate(operator, sector);
    Room r105 = room(sector, "Sala 105");
    Room r203 = room(sector, "Sala 203");
    Item item = item(inventory(user(TestAccess.ADMIN), sector), "I-1", r105);

    Map<String, Object> body = putBody(item, true, null);
    assertThat(
            call(operator, HttpMethod.PUT, "/item/" + item.id(), body, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    body = putBody(item, true, r203.id());
    var response = call(operator, HttpMethod.PUT, "/item/" + item.id(), body, Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("divergent")).isEqualTo(true);
    assertThat(audits(operator, item.id())).hasSize(1);

    // Desmarcar limpa a sala encontrada.
    body = putBody(item, false, null);
    var cleared = call(operator, HttpMethod.PUT, "/item/" + item.id(), body, Map.class);
    assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(cleared.getBody().get("isValid")).isEqualTo(false);
    assertThat(cleared.getBody().get("foundRoomId")).isNull();
    assertThat(cleared.getBody().get("divergent")).isEqualTo(false);
  }

  private Map<String, Object> putBody(Item item, boolean valid, Long foundRoomId) {
    Map<String, Object> body = new HashMap<>();
    body.put("id", item.id());
    body.put("code", item.code());
    body.put("description", item.description());
    body.put("responsible", item.responsible());
    body.put("price", "10,00");
    body.put("locale", item.locale());
    body.put("roomId", item.room() != null ? item.room().id() : null);
    body.put("isValid", valid);
    body.put("foundRoomId", foundRoomId);
    body.put("observations", List.of());
    return body;
  }

  @Test
  void operatorCannotChangeTheRegisteredRoom_butManagerCan() {
    Sector sector = sector();
    User operator = user(TestAccess.OPERATOR);
    User manager = user(TestAccess.MANAGER);
    access.allocate(operator, sector);
    access.allocate(manager, sector);
    Room r105 = room(sector, "Sala 105");
    Room r203 = room(sector, "Sala 203");
    Item item = item(inventory(user(TestAccess.ADMIN), sector), "I-1", r105);

    Map<String, Object> body = putBody(item, false, null);
    body.put("roomId", r203.id());
    assertThat(
            call(operator, HttpMethod.PUT, "/item/" + item.id(), body, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    var changed = call(manager, HttpMethod.PUT, "/item/" + item.id(), body, Map.class);
    assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(changed.getBody().get("roomName")).isEqualTo("Sala 203");
    assertThat(changed.getBody().get("locale")).isEqualTo("Sala 203");
  }

  @Test
  void managerChangingTheRoomByText_findsOrCreatesTheRoom() {
    Sector sector = sector();
    User manager = user(TestAccess.MANAGER);
    access.allocate(manager, sector);
    Room r105 = room(sector, "Sala 105");
    Item item = item(inventory(user(TestAccess.ADMIN), sector), "I-1", r105);

    Map<String, Object> body = putBody(item, false, null);
    body.put("roomId", null);
    body.put("locale", "  sala   105 ");
    var same = call(manager, HttpMethod.PUT, "/item/" + item.id(), body, Map.class);
    assertThat(same.getBody().get("roomName")).isEqualTo("Sala 105");
    assertThat(roomRepository.countBySector_Id(sector.id())).isEqualTo(1);

    body.put("locale", "Almoxarifado");
    var created = call(manager, HttpMethod.PUT, "/item/" + item.id(), body, Map.class);
    assertThat(created.getBody().get("roomName")).isEqualTo("Almoxarifado");
    assertThat(roomRepository.countBySector_Id(sector.id())).isEqualTo(2);
  }

  @Test
  void finishedInventory_rejectsValidation() {
    Sector sector = sector();
    User operator = user(TestAccess.OPERATOR);
    access.allocate(operator, sector);
    Room r105 = room(sector, "Sala 105");
    Inventory inventory = inventory(user(TestAccess.ADMIN), sector);
    Item item = item(inventory, "I-1", r105);
    inventory.finish(LocalDateTime.now());
    inventoryRepository.save(inventory);

    assertThat(validateStatus(operator, item.id(), r105.id())).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void sheetPreview_listsNewAndExistingRooms_withoutCreatingAnything() {
    Sector sector = sector();
    User manager = user(TestAccess.MANAGER);
    access.allocate(manager, sector);
    room(sector, "Sala 105");
    Inventory inventory = inventory(user(TestAccess.ADMIN), sector);
    String csv =
        CSV_HEADER
            + "A1;Mesa;Fulano;100,00;sala  105;\n"
            + "A2;Cadeira;Fulano;50,00;Laboratório 1;\n"
            + "A3;Quadro;Fulano;70,00;LABORATORIO 1;\n"
            + "A4;Mouse;Fulano;20,00;;\n";

    var response =
        uploadCsv(
            manager, "/inventory/validate-sheet", Map.of("inventoryId", "" + inventory.id()), csv);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"newRooms\":[\"Laboratório 1\"]");
    assertThat(response.getBody()).contains("\"existingRooms\":[\"sala 105\"]");
    assertThat(roomRepository.countBySector_Id(sector.id())).isEqualTo(1);
  }

  @Test
  void sheetImport_createsOnlyMissingRooms_andLinksItems() {
    Sector sector = sector();
    User manager = user(TestAccess.MANAGER);
    access.allocate(manager, sector);
    Room r105 = room(sector, "Sala 105");
    Inventory inventory = inventory(user(TestAccess.ADMIN), sector);
    String csv =
        CSV_HEADER
            + "A1;Mesa;Fulano;100,00;sala  105;\n"
            + "A2;Cadeira;Fulano;50,00;Laboratório 1;\n"
            + "A3;Quadro;Fulano;70,00;LABORATORIO 1;\n"
            + "A4;Mouse;Fulano;20,00;;\n";

    var response =
        uploadCsv(manager, "/inventory/add-items", Map.of("inventoryId", "" + inventory.id()), csv);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).contains("\"createdRooms\":1");
    assertThat(roomRepository.countBySector_Id(sector.id())).isEqualTo(2);
    List<Item> items =
        itemRepository
            .findByInventory_Id(inventory.id(), org.springframework.data.domain.Pageable.unpaged())
            .getContent();
    Map<String, Item> byCode = new HashMap<>();
    items.forEach(i -> byCode.put(i.code(), i));
    assertThat(byCode.get("A1").room().id()).isEqualTo(r105.id());
    assertThat(byCode.get("A2").room().name()).isEqualTo("Laboratório 1");
    assertThat(byCode.get("A3").room().id()).isEqualTo(byCode.get("A2").room().id());
    assertThat(byCode.get("A4").room()).isNull();
  }

  @Test
  void addSingleItem_findsOrCreatesTheRoom() {
    Sector sector = sector();
    User manager = user(TestAccess.MANAGER);
    access.allocate(manager, sector);
    Room r105 = room(sector, "Sala 105");
    Inventory inventory = inventory(user(TestAccess.ADMIN), sector);

    Map<String, Object> body = new HashMap<>();
    body.put("code", "S-1");
    body.put("description", "Mesa");
    body.put("responsible", "Fulano");
    body.put("locale", "SALA 105");
    var response =
        call(
            manager,
            HttpMethod.POST,
            "/inventory/add-single-item?inventoryId=" + inventory.id(),
            body,
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Item saved =
        itemRepository
            .findByInventory_Id(inventory.id(), org.springframework.data.domain.Pageable.unpaged())
            .getContent()
            .get(0);
    assertThat(saved.room().id()).isEqualTo(r105.id());
    assertThat(roomRepository.countBySector_Id(sector.id())).isEqualTo(1);
  }
}
