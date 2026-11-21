package clp.inventory.itemaudit;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.model.Inventory;
import clp.inventory.model.InventoryAudit;
import clp.inventory.model.Item;
import clp.inventory.model.Sector;
import clp.inventory.model.User;
import clp.inventory.repository.InventoryAuditRepository;
import clp.inventory.repository.InventoryRepository;
import clp.inventory.repository.ItemRepository;
import clp.inventory.repository.SectorRepository;
import clp.inventory.testsupport.TestAccess;
import clp.inventory.testsupport.TestItems;
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
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Regras de edição de item e de reabertura do inventário (Postgres real via Testcontainers, HTTP
 * real): item validado ou de inventário finalizado só aceita observações, desvalidar é ato de
 * gestão com auditoria, toda edição deixa trilha e a correção depois do fechamento passa por POST
 * /inventory/{id}/reopen.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ItemEditRulesE2ETest {

  private static final String TEST_SECRET = "test-secret-de-pelo-menos-32-bytes-aqui";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void authProperties(DynamicPropertyRegistry registry) {
    registry.add("security.token.secret", () -> TEST_SECRET);
  }

  @Autowired TestRestTemplate http;

  @Autowired TestAccess access;
  @Autowired TestItems testItems;

  @Autowired SectorRepository sectors;

  @Autowired InventoryRepository inventories;

  @Autowired ItemRepository items;

  @Autowired InventoryAuditRepository inventoryAudits;

  /** Um setor com gestor, operador e consulta, e um inventário aberto com um item. */
  private final class Scenario {
    final Sector sector = sectors.save(new Sector("Setor " + System.nanoTime(), null));
    final User admin = access.user("admin-" + System.nanoTime() + "@ifpe.edu.br", TestAccess.ADMIN);
    final User manager = member(TestAccess.MANAGER);
    final User operator = member(TestAccess.OPERATOR);
    final User consult = member(TestAccess.CONSULT);
    final Inventory inventory;
    final Item item;

    Scenario() {
      Inventory created =
          new Inventory(
              "Inventario " + System.nanoTime(), "", LocalDate.now().plusDays(30), admin, sector);
      created.addItem(new Item("COD-" + System.nanoTime(), "", "Mesa", 1000, "Sala 1", "Maria"));
      inventory = inventories.save(created);
      item = inventory.items().get(0);
    }

    private User member(String role) {
      User user = access.user("u-" + System.nanoTime() + "@ifpe.edu.br", role);
      access.allocate(user, sector);
      return user;
    }

    Map<String, Object> body(boolean valid, String description) {
      return itemBody(items.findById(item.id()).orElseThrow(), valid, description);
    }

    Item stored() {
      return items.findById(item.id()).orElseThrow();
    }

    String itemUrl() {
      return "/item/" + item.id();
    }

    String inventoryUrl() {
      return "/inventory/" + inventory.id();
    }

    void validateAs(User user) {
      assertThat(status(user, HttpMethod.PATCH, itemUrl() + "/validate", null))
          .isEqualTo(HttpStatus.OK);
    }

    /**
     * Finaliza pela API; um item ainda pendente é validado antes (a regra exige zero pendentes).
     */
    void finalizeInventory() {
      if (stored().validationStatus() == Item.ValidationStatus.PENDENTE) {
        testItems.validateAll(inventory.id());
      }
      assertThat(status(manager, HttpMethod.POST, inventoryUrl() + "/finalize", null))
          .isEqualTo(HttpStatus.OK);
    }

    /**
     * Fecha o inventário direto no banco, sem a regra de finalização: reproduz um inventário
     * finalizado antes dela, que pode ter item pendente.
     */
    void finishDirectly() {
      Inventory stored = inventories.findById(inventory.id()).orElseThrow();
      stored.finish(LocalDateTime.now());
      inventories.save(stored);
    }

    List<InventoryAudit> itemUpdates() {
      return inventoryAudits.findByInventoryIdOrderByIdAsc(inventory.id()).stream()
          .filter(a -> "UPDATE_ITEM".equals(a.actionType()))
          .toList();
    }

    List<InventoryAudit> reopens() {
      return inventoryAudits.findByInventoryIdOrderByIdAsc(inventory.id()).stream()
          .filter(a -> "REOPEN_INVENTORY".equals(a.actionType()))
          .toList();
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> itemAudits(String eventType) {
      List<Map<String, Object>> all =
          call(manager, HttpMethod.GET, "/item-audits?itemId=" + item.id(), null, List.class)
              .getBody();
      return all.stream().filter(a -> eventType.equals(a.get("eventType"))).toList();
    }
  }

  private HttpHeaders headers(User user) {
    HttpHeaders headers = new HttpHeaders();
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
      User user, HttpMethod method, String url, Object body, Class<T> type) {
    return http.exchange(url, method, new HttpEntity<>(body, headers(user)), type);
  }

  private HttpStatus status(User user, HttpMethod method, String url, Object body) {
    return HttpStatus.valueOf(call(user, method, url, body, String.class).getStatusCode().value());
  }

  private static Map<String, Object> itemBody(Item item, boolean valid, String description) {
    Map<String, Object> body = new HashMap<>();
    body.put("id", item.id());
    body.put("code", item.code());
    body.put("description", description);
    body.put("responsible", item.responsible());
    body.put("price", "R$ 10,00");
    body.put("locale", item.locale());
    body.put("isValid", valid);
    return body;
  }

  // ------------------------------------------------- edição de dados do item

  @Test
  void managerEditsAnUnvalidatedItemAndTheEditIsAudited() {
    Scenario s = new Scenario();
    Map<String, Object> body = s.body(false, "Mesa nova");
    body.put("price", "R$ 25,50");

    assertThat(status(s.manager, HttpMethod.PUT, s.itemUrl(), body)).isEqualTo(HttpStatus.OK);

    assertThat(s.stored().description()).isEqualTo("Mesa nova");
    assertThat(s.stored().price()).isEqualTo(2550);
    List<InventoryAudit> updates = s.itemUpdates();
    assertThat(updates).hasSize(1);
    InventoryAudit audit = updates.get(0);
    assertThat(audit.itemId()).isEqualTo(s.item.id());
    assertThat(audit.actorUser().getId()).isEqualTo(s.manager.getId());
    assertThat(audit.actorSectorId()).isEqualTo(s.sector.id());
    assertThat(audit.status()).isEqualTo("SUCCESS");
    assertThat(audit.details())
        .contains("description: \"Mesa\" -> \"Mesa nova\"")
        .contains("price: 10.00 -> 25.50")
        .doesNotContain("code:");
  }

  @Test
  void adminEditIsAuditedWithoutASector() {
    Scenario s = new Scenario();

    assertThat(status(s.admin, HttpMethod.PUT, s.itemUrl(), s.body(false, "Feita pelo admin")))
        .isEqualTo(HttpStatus.OK);

    assertThat(s.itemUpdates()).hasSize(1);
    assertThat(s.itemUpdates().get(0).actorSectorId()).isNull();
  }

  @Test
  void aPutWithoutChangesDoesNotRecordAnAudit() {
    Scenario s = new Scenario();

    assertThat(status(s.manager, HttpMethod.PUT, s.itemUrl(), s.body(false, "Mesa")))
        .isEqualTo(HttpStatus.OK);

    assertThat(s.itemUpdates()).isEmpty();
  }

  @Test
  void operatorCannotEditItemData() {
    Scenario s = new Scenario();

    assertThat(status(s.operator, HttpMethod.PUT, s.itemUrl(), s.body(false, "Outra")))
        .isEqualTo(HttpStatus.FORBIDDEN);

    assertThat(s.stored().description()).isEqualTo("Mesa");
    assertThat(s.itemUpdates()).isEmpty();
  }

  @Test
  void validatedItemRejectsDataChangesEvenFromTheManager() {
    Scenario s = new Scenario();
    s.validateAs(s.operator);

    assertThat(status(s.manager, HttpMethod.PUT, s.itemUrl(), s.body(true, "Adulterada")))
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(status(s.admin, HttpMethod.PUT, s.itemUrl(), s.body(true, "Adulterada")))
        .isEqualTo(HttpStatus.CONFLICT);

    assertThat(s.stored().description()).isEqualTo("Mesa");
    assertThat(s.stored().isValid()).isTrue();
    assertThat(s.itemUpdates()).isEmpty();
  }

  @Test
  void validatedItemAcceptsAPutThatChangesNothing() {
    Scenario s = new Scenario();
    s.validateAs(s.operator);

    assertThat(status(s.manager, HttpMethod.PUT, s.itemUrl(), s.body(true, "Mesa")))
        .isEqualTo(HttpStatus.OK);

    assertThat(s.stored().isValid()).isTrue();
  }

  @Test
  void putRecusaValorAusenteOuMalformadoSemAlterarOPrecoNemResponderOk() {
    Scenario s = new Scenario();

    for (String bad : new String[] {"abc", "-10", "1,500.00", "10,999", "", "   "}) {
      Map<String, Object> body = s.body(false, "Mesa");
      body.put("price", bad);
      assertThat(status(s.manager, HttpMethod.PUT, s.itemUrl(), body))
          .as("valor '%s'", bad)
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
    Map<String, Object> absent = s.body(false, "Mesa");
    absent.remove("price");
    assertThat(status(s.manager, HttpMethod.PUT, s.itemUrl(), absent))
        .isEqualTo(HttpStatus.BAD_REQUEST);

    assertThat(s.stored().price()).isEqualTo(1000);
    assertThat(s.itemUpdates()).isEmpty();
  }

  @Test
  void putAceitaOMesmoTextoQueAListagemMostraEOsFormatosDeValor() {
    Scenario s = new Scenario();

    // "R$ 1.234,56" com espaço não separável é o que a listagem devolve e o front manda de volta.
    Map<String, Object> listed = s.body(false, "Mesa");
    listed.put("price", "R$\u00A01.234,56");
    assertThat(status(s.manager, HttpMethod.PUT, s.itemUrl(), listed)).isEqualTo(HttpStatus.OK);
    assertThat(s.stored().price()).isEqualTo(123456);

    Map<String, Object> number = s.body(false, "Mesa");
    number.put("price", 1500.5);
    assertThat(status(s.manager, HttpMethod.PUT, s.itemUrl(), number)).isEqualTo(HttpStatus.OK);
    assertThat(s.stored().price()).isEqualTo(150050);
  }

  @Test
  void finalizedInventoryRejectsDataChangesOnAnUnvalidatedItem() {
    Scenario s = new Scenario();
    // Inventário finalizado antes da regra de completude, com item ainda pendente: o guard vale.
    s.finishDirectly();
    Map<String, Object> body = s.body(false, "Mesa");
    body.put("price", "999999");

    assertThat(status(s.manager, HttpMethod.PUT, s.itemUrl(), body)).isEqualTo(HttpStatus.CONFLICT);
    assertThat(status(s.admin, HttpMethod.PUT, s.itemUrl(), body)).isEqualTo(HttpStatus.CONFLICT);

    assertThat(s.stored().price()).isEqualTo(1000);
    assertThat(s.itemUpdates()).isEmpty();
  }

  @Test
  void notesStayEditableOnValidatedItemsAndAfterTheInventoryIsFinalized() {
    Scenario s = new Scenario();
    List<Map<String, String>> notes = List.of(Map.of("content", "Conferido com etiqueta"));
    s.validateAs(s.operator);

    assertThat(status(s.operator, HttpMethod.PATCH, s.itemUrl() + "/notes", notes))
        .isEqualTo(HttpStatus.OK);
    s.finalizeInventory();
    assertThat(status(s.manager, HttpMethod.PATCH, s.itemUrl() + "/notes", notes))
        .isEqualTo(HttpStatus.OK);

    var item = call(s.manager, HttpMethod.GET, s.itemUrl(), null, Map.class);
    assertThat((List<?>) item.getBody().get("observations")).hasSize(1);
  }

  // ------------------------------------------------------- desvalidar o item

  @Test
  void managerInvalidatesAValidatedItemAndItBecomesEditable() {
    Scenario s = new Scenario();
    s.validateAs(s.operator);

    var response =
        call(
            s.manager,
            HttpMethod.PATCH,
            s.itemUrl() + "/validate",
            Map.of("isValid", false),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("isValid")).isEqualTo(false);
    assertThat(response.getBody().get("validatedAt")).isNull();
    assertThat(s.stored().isValid()).isFalse();
    var revoked = s.itemAudits("VALIDATION_REVOKED");
    assertThat(revoked).hasSize(1);
    assertThat(revoked.get(0).get("status")).isEqualTo("REVOKED");
    assertThat(revoked.get(0).get("actorUserId")).isEqualTo(s.manager.getId().intValue());
    assertThat(revoked.get(0).get("actorSectorId")).isEqualTo((int) s.sector.id());
    assertThat((String) revoked.get(0).get("details")).startsWith("Validado em ");

    assertThat(status(s.manager, HttpMethod.PUT, s.itemUrl(), s.body(false, "Corrigida")))
        .isEqualTo(HttpStatus.OK);
    assertThat(s.stored().description()).isEqualTo("Corrigida");
  }

  @Test
  void operatorAndConsultCannotInvalidate() {
    Scenario s = new Scenario();
    s.validateAs(s.operator);

    for (User user : List.of(s.operator, s.consult)) {
      assertThat(
              status(user, HttpMethod.PATCH, s.itemUrl() + "/validate", Map.of("isValid", false)))
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(status(user, HttpMethod.PUT, s.itemUrl(), s.body(false, "Mesa")))
          .isEqualTo(HttpStatus.FORBIDDEN);
    }

    assertThat(s.stored().isValid()).isTrue();
    assertThat(s.itemAudits("VALIDATION_REVOKED")).isEmpty();
  }

  @Test
  void invalidatingAnItemThatIsNotValidatedIsAConflict() {
    Scenario s = new Scenario();

    assertThat(
            status(
                s.manager, HttpMethod.PATCH, s.itemUrl() + "/validate", Map.of("isValid", false)))
        .isEqualTo(HttpStatus.CONFLICT);

    assertThat(s.itemAudits("VALIDATION_REVOKED")).isEmpty();
  }

  @Test
  void invalidatingIsBlockedWhileTheInventoryIsFinalized() {
    Scenario s = new Scenario();
    s.validateAs(s.operator);
    s.finalizeInventory();

    assertThat(
            status(
                s.manager, HttpMethod.PATCH, s.itemUrl() + "/validate", Map.of("isValid", false)))
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(status(s.manager, HttpMethod.PUT, s.itemUrl(), s.body(false, "Mesa")))
        .isEqualTo(HttpStatus.CONFLICT);

    assertThat(s.stored().isValid()).isTrue();
    assertThat(s.itemAudits("VALIDATION_REVOKED")).isEmpty();
  }

  @Test
  void putThatInvalidatesGoesThroughTheSameRulesAndAudit() {
    Scenario s = new Scenario();
    s.validateAs(s.operator);

    assertThat(status(s.manager, HttpMethod.PUT, s.itemUrl(), s.body(false, "Mesa")))
        .isEqualTo(HttpStatus.OK);

    assertThat(s.stored().isValid()).isFalse();
    assertThat(s.itemAudits("VALIDATION_REVOKED")).hasSize(1);
  }

  @Test
  void putCannotInvalidateAndEditInTheSameRequest() {
    Scenario s = new Scenario();
    s.validateAs(s.operator);

    assertThat(status(s.manager, HttpMethod.PUT, s.itemUrl(), s.body(false, "Trocada junto")))
        .isEqualTo(HttpStatus.CONFLICT);

    assertThat(s.stored().isValid()).isTrue();
    assertThat(s.stored().description()).isEqualTo("Mesa");
    assertThat(s.itemAudits("VALIDATION_REVOKED")).isEmpty();
    assertThat(s.itemUpdates()).isEmpty();
  }

  // ------------------------------------------------------ reabrir o inventário

  @Test
  void managerReopensAFinalizedInventoryAndTheReopenIsAudited() {
    Scenario s = new Scenario();
    s.finalizeInventory();

    var response = call(s.manager, HttpMethod.POST, s.inventoryUrl() + "/reopen", null, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("status")).isEqualTo("EM_ANDAMENTO");
    assertThat(response.getBody().get("completedAt")).isNull();
    Inventory stored = inventories.findById(s.inventory.id()).orElseThrow();
    assertThat(stored.status()).isEqualTo(Inventory.Status.EM_ANDAMENTO);
    assertThat(stored.completedAt()).isNull();
    List<InventoryAudit> reopens = s.reopens();
    assertThat(reopens).hasSize(1);
    assertThat(reopens.get(0).actorUser().getId()).isEqualTo(s.manager.getId());
    assertThat(reopens.get(0).actorSectorId()).isEqualTo(s.sector.id());
    assertThat(reopens.get(0).status()).isEqualTo("SUCCESS");
    assertThat(reopens.get(0).details()).startsWith("Finalizado em ");
  }

  @Test
  void afterReopeningTheManagerCanCorrectAndFinalizeAgain() {
    Scenario s = new Scenario();
    s.finalizeInventory();
    assertThat(status(s.manager, HttpMethod.PUT, s.itemUrl(), s.body(false, "Corrigida")))
        .isEqualTo(HttpStatus.CONFLICT);

    assertThat(status(s.admin, HttpMethod.POST, s.inventoryUrl() + "/reopen", null))
        .isEqualTo(HttpStatus.OK);
    // O item continua validado: só aceita observações até o gestor desvalidá-lo.
    assertThat(status(s.manager, HttpMethod.PUT, s.itemUrl(), s.body(false, "Corrigida")))
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            status(
                s.manager, HttpMethod.PATCH, s.itemUrl() + "/validate", Map.of("isValid", false)))
        .isEqualTo(HttpStatus.OK);
    assertThat(status(s.manager, HttpMethod.PUT, s.itemUrl(), s.body(false, "Corrigida")))
        .isEqualTo(HttpStatus.OK);
    // Pendente de novo, o inventário só finaliza depois de conferir o item.
    assertThat(status(s.manager, HttpMethod.POST, s.inventoryUrl() + "/finalize", null))
        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    s.finalizeInventory();

    assertThat(s.stored().description()).isEqualTo("Corrigida");
    assertThat(inventories.findById(s.inventory.id()).orElseThrow().status())
        .isEqualTo(Inventory.Status.CONCLUIDO);
    assertThat(s.reopens()).hasSize(1);
    assertThat(s.reopens().get(0).actorSectorId()).isNull();
  }

  @Test
  void onlyAdminAndSectorManagerReopen() {
    Scenario s = new Scenario();
    s.finalizeInventory();
    Scenario other = new Scenario();

    for (User user : List.of(s.operator, s.consult, other.manager)) {
      assertThat(status(user, HttpMethod.POST, s.inventoryUrl() + "/reopen", null))
          .isEqualTo(HttpStatus.FORBIDDEN);
    }

    assertThat(inventories.findById(s.inventory.id()).orElseThrow().status())
        .isEqualTo(Inventory.Status.CONCLUIDO);
    assertThat(s.reopens()).isEmpty();
  }

  @Test
  void reopeningAnInventoryThatIsNotFinalizedIsAConflict() {
    Scenario s = new Scenario();

    assertThat(status(s.manager, HttpMethod.POST, s.inventoryUrl() + "/reopen", null))
        .isEqualTo(HttpStatus.CONFLICT);

    assertThat(s.reopens()).isEmpty();
  }

  @Test
  void reopeningAnUnknownInventoryIsNotFound() {
    Scenario s = new Scenario();

    assertThat(status(s.admin, HttpMethod.POST, "/inventory/999999/reopen", null))
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
