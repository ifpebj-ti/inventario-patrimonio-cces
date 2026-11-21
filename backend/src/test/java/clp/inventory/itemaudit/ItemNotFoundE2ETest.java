package clp.inventory.itemaudit;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.model.Inventory;
import clp.inventory.model.Item;
import clp.inventory.model.ItemAudit;
import clp.inventory.model.Sector;
import clp.inventory.model.User;
import clp.inventory.repository.InventoryRepository;
import clp.inventory.repository.ItemAuditRepository;
import clp.inventory.repository.ItemRepository;
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
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Situação do item (PENDENTE, VALIDADO, NAO_LOCALIZADO) e regra de finalização (Postgres real via
 * Testcontainers, HTTP real): o operador marca o item não encontrado com justificativa, o gestor
 * desfaz, e o inventário só finaliza com itens e sem nenhum pendente.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ItemNotFoundE2ETest {

  private static final String TEST_SECRET = "test-secret-de-pelo-menos-32-bytes-aqui";
  private static final String REASON = "Procurado nas salas 101 e 102; devolvido ao almoxarifado.";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void authProperties(DynamicPropertyRegistry registry) {
    registry.add("security.token.secret", () -> TEST_SECRET);
  }

  @Autowired TestRestTemplate http;
  @Autowired TestAccess access;
  @Autowired SectorRepository sectors;
  @Autowired InventoryRepository inventories;
  @Autowired ItemRepository items;
  @Autowired ItemAuditRepository itemAudits;

  /** Um setor com gestor, operador e consulta, e um inventário aberto com dois itens pendentes. */
  private final class Scenario {
    final Sector sector = sectors.save(new Sector("Setor " + System.nanoTime(), null));
    final User admin = access.user("admin-" + System.nanoTime() + "@ifpe.edu.br", TestAccess.ADMIN);
    final User manager = member(TestAccess.MANAGER);
    final User operator = member(TestAccess.OPERATOR);
    final User consult = member(TestAccess.CONSULT);
    final Inventory inventory;
    final Item first;
    final Item second;

    Scenario() {
      Inventory created =
          new Inventory(
              "Inventario " + System.nanoTime(), "", LocalDate.now().plusDays(30), admin, sector);
      created.addItem(new Item("A-" + System.nanoTime(), "", "Mesa", 1000, null, "Maria"));
      created.addItem(new Item("B-" + System.nanoTime(), "", "Cadeira", 2000, null, "Joao"));
      inventory = inventories.save(created);
      List<Item> saved = inventory.items();
      first = saved.get(0);
      second = saved.get(1);
    }

    private User member(String role) {
      User user = access.user("u-" + System.nanoTime() + "@ifpe.edu.br", role);
      access.allocate(user, sector);
      return user;
    }

    String url(Item item, String suffix) {
      return "/item/" + item.id() + suffix;
    }

    Item stored(Item item) {
      return items.findById(item.id()).orElseThrow();
    }

    HttpStatus markNotFound(User user, Item item, String reason) {
      Map<String, Object> body = new HashMap<>();
      body.put("reason", reason);
      return status(user, HttpMethod.PATCH, url(item, "/not-found"), body);
    }

    HttpStatus validate(User user, Item item) {
      return status(user, HttpMethod.PATCH, url(item, "/validate"), null);
    }

    HttpStatus revert(User user, Item item) {
      return status(user, HttpMethod.PATCH, url(item, "/validate"), Map.of("isValid", false));
    }

    ResponseEntity<Map> finalizeInventory(User user) {
      return call(user, HttpMethod.POST, "/inventory/" + inventory.id() + "/finalize", null);
    }

    List<ItemAudit> audits(Item item, String eventType) {
      return itemAudits.findByItem_Id(item.id()).stream()
          .filter(a -> eventType.equals(a.eventType()))
          .toList();
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

  private ResponseEntity<Map> call(User user, HttpMethod method, String url, Object body) {
    return http.exchange(url, method, new HttpEntity<>(body, headers(user)), Map.class);
  }

  private HttpStatus status(User user, HttpMethod method, String url, Object body) {
    return HttpStatus.valueOf(
        http.exchange(url, method, new HttpEntity<>(body, headers(user)), String.class)
            .getStatusCode()
            .value());
  }

  // ----------------------------------------------------------- marcar como não localizado

  @Test
  void operatorMarksAPendingItemAsNotFoundWithAReason() {
    Scenario s = new Scenario();

    var response =
        call(
            s.operator,
            HttpMethod.PATCH,
            s.url(s.first, "/not-found"),
            Map.of("reason", "  " + REASON + "  "));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("validationStatus")).isEqualTo("NAO_LOCALIZADO");
    // Não localizado foi conferido, mas não é "validado".
    assertThat(response.getBody().get("isValid")).isEqualTo(false);
    assertThat(response.getBody().get("validatedAt")).isNotNull();

    Item stored = s.stored(s.first);
    assertThat(stored.validationStatus()).isEqualTo(Item.ValidationStatus.NAO_LOCALIZADO);
    assertThat(stored.foundRoom()).isNull();
    // A justificativa também vira uma observação do item (a coleção é lazy: confere pela resposta).
    List<?> observations = (List<?>) response.getBody().get("observations");
    assertThat(observations).hasSize(1);
    assertThat(observations.get(0).toString()).contains("Não localizado: " + REASON);
    List<ItemAudit> audits = s.audits(s.first, "MARKED_NOT_FOUND");
    assertThat(audits).hasSize(1);
    assertThat(audits.get(0).details()).isEqualTo(REASON);
    assertThat(audits.get(0).actorUser().getId()).isEqualTo(s.operator.getId());
    assertThat(audits.get(0).status()).isEqualTo("NOT_FOUND");
  }

  @Test
  void theReasonIsRequiredAndLimited() {
    Scenario s = new Scenario();

    for (String bad : new String[] {null, "", "   ", "x".repeat(256)}) {
      assertThat(s.markNotFound(s.operator, s.first, bad))
          .as("motivo '%s'", bad == null ? null : bad.length() + " caracteres")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
    assertThat(status(s.operator, HttpMethod.PATCH, s.url(s.first, "/not-found"), Map.of()))
        .isEqualTo(HttpStatus.BAD_REQUEST);

    assertThat(s.stored(s.first).validationStatus()).isEqualTo(Item.ValidationStatus.PENDENTE);
    assertThat(s.audits(s.first, "MARKED_NOT_FOUND")).isEmpty();
    // O limite é inclusivo: 255 caracteres passam.
    assertThat(s.markNotFound(s.operator, s.first, "x".repeat(255))).isEqualTo(HttpStatus.OK);
  }

  @Test
  void whoCannotOperateTheInventoryCannotMarkItems() {
    Scenario s = new Scenario();
    Scenario other = new Scenario();

    assertThat(s.markNotFound(s.consult, s.first, REASON)).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(s.markNotFound(other.manager, s.first, REASON)).isEqualTo(HttpStatus.FORBIDDEN);

    assertThat(s.stored(s.first).validationStatus()).isEqualTo(Item.ValidationStatus.PENDENTE);
  }

  @Test
  void onlyAPendingItemCanBeMarked() {
    Scenario s = new Scenario();
    assertThat(s.validate(s.operator, s.first)).isEqualTo(HttpStatus.OK);
    assertThat(s.markNotFound(s.operator, s.second, REASON)).isEqualTo(HttpStatus.OK);

    assertThat(s.markNotFound(s.operator, s.first, REASON)).isEqualTo(HttpStatus.CONFLICT);
    assertThat(s.markNotFound(s.operator, s.second, REASON)).isEqualTo(HttpStatus.CONFLICT);

    assertThat(s.stored(s.first).validationStatus()).isEqualTo(Item.ValidationStatus.VALIDADO);
    assertThat(s.audits(s.second, "MARKED_NOT_FOUND")).hasSize(1);
  }

  @Test
  void cannotMarkAnItemOfAFinalizedInventory() {
    Scenario s = new Scenario();
    Inventory stored = inventories.findById(s.inventory.id()).orElseThrow();
    stored.finish(LocalDateTime.now());
    inventories.save(stored);

    assertThat(s.markNotFound(s.manager, s.first, REASON)).isEqualTo(HttpStatus.CONFLICT);

    assertThat(s.stored(s.first).validationStatus()).isEqualTo(Item.ValidationStatus.PENDENTE);
  }

  @Test
  void unknownItemIsNotFound() {
    Scenario s = new Scenario();

    assertThat(
            status(
                s.operator,
                HttpMethod.PATCH,
                "/item/999999999/not-found",
                Map.of("reason", REASON)))
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  // ------------------------------------------------------ item não localizado fica travado

  @Test
  void aNotFoundItemCannotBeValidatedDirectlyNorHaveItsDataEdited() {
    Scenario s = new Scenario();
    assertThat(s.markNotFound(s.operator, s.first, REASON)).isEqualTo(HttpStatus.OK);

    // Validar direto: o gestor precisa desfazer a marcação antes.
    assertThat(s.validate(s.operator, s.first)).isEqualTo(HttpStatus.CONFLICT);
    assertThat(s.validate(s.manager, s.first)).isEqualTo(HttpStatus.CONFLICT);
    // Dados: só observações, como no item validado.
    Map<String, Object> edit = new HashMap<>();
    edit.put("id", s.first.id());
    edit.put("code", s.first.code());
    edit.put("description", "Outra descrição");
    edit.put("responsible", s.first.responsible());
    edit.put("price", "R$ 10,00");
    edit.put("isValid", false);
    assertThat(status(s.manager, HttpMethod.PUT, s.url(s.first, ""), edit))
        .isEqualTo(HttpStatus.CONFLICT);

    Item stored = s.stored(s.first);
    assertThat(stored.validationStatus()).isEqualTo(Item.ValidationStatus.NAO_LOCALIZADO);
    assertThat(stored.description()).isNotEqualTo("Outra descrição");
  }

  @Test
  void onlyTheManagerUndoesTheMarkAndTheItemBecomesPendingAgain() {
    Scenario s = new Scenario();
    assertThat(s.markNotFound(s.operator, s.first, REASON)).isEqualTo(HttpStatus.OK);

    assertThat(s.revert(s.operator, s.first)).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(s.stored(s.first).validationStatus())
        .isEqualTo(Item.ValidationStatus.NAO_LOCALIZADO);

    assertThat(s.revert(s.manager, s.first)).isEqualTo(HttpStatus.OK);

    Item stored = s.stored(s.first);
    assertThat(stored.validationStatus()).isEqualTo(Item.ValidationStatus.PENDENTE);
    assertThat(stored.validatedAt()).isNull();
    List<ItemAudit> revoked = s.audits(s.first, "VALIDATION_REVOKED");
    assertThat(revoked).hasSize(1);
    assertThat(revoked.get(0).details()).startsWith("Marcado como não localizado em ");
    // De volta a pendente, o item pode ser validado normalmente (encontrado depois).
    assertThat(s.validate(s.operator, s.first)).isEqualTo(HttpStatus.OK);
    assertThat(s.stored(s.first).validationStatus()).isEqualTo(Item.ValidationStatus.VALIDADO);
  }

  @Test
  void revertingAPendingItemIsAConflict() {
    Scenario s = new Scenario();

    assertThat(s.revert(s.manager, s.first)).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void theItemDetailExposesTheValidationStatus() {
    Scenario s = new Scenario();

    assertThat(call(s.manager, HttpMethod.GET, s.url(s.first, ""), null).getBody())
        .containsEntry("validationStatus", "PENDENTE")
        .containsEntry("isValid", false);
    assertThat(s.validate(s.operator, s.first)).isEqualTo(HttpStatus.OK);
    assertThat(call(s.manager, HttpMethod.GET, s.url(s.first, ""), null).getBody())
        .containsEntry("validationStatus", "VALIDADO")
        .containsEntry("isValid", true);
  }

  // ----------------------------------------------------------------- regra de finalização

  @Test
  void cannotFinalizeWithPendingItemsAndTheBodyTellsHowMany() {
    Scenario s = new Scenario();
    assertThat(s.validate(s.operator, s.first)).isEqualTo(HttpStatus.OK);

    var response = s.finalizeInventory(s.manager);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(((Number) response.getBody().get("pendingItems")).longValue()).isEqualTo(1);
    assertThat(response.getBody().get("message").toString())
        .contains("1 item ainda está pendente")
        .contains("não localizado");
    assertThat(inventories.findById(s.inventory.id()).orElseThrow().status())
        .isEqualTo(Inventory.Status.EM_ANDAMENTO);
  }

  @Test
  void theRuleAppliesToTheAdministratorToo() {
    Scenario s = new Scenario();

    var response = s.finalizeInventory(s.admin);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(((Number) response.getBody().get("pendingItems")).longValue()).isEqualTo(2);
    assertThat(response.getBody().get("message").toString())
        .contains("2 itens ainda estão pendentes");
  }

  @Test
  void aNotFoundItemCountsAsCheckedSoALostGoodDoesNotBlockTheClosing() {
    Scenario s = new Scenario();
    assertThat(s.validate(s.operator, s.first)).isEqualTo(HttpStatus.OK);
    assertThat(s.markNotFound(s.operator, s.second, REASON)).isEqualTo(HttpStatus.OK);

    var response = s.finalizeInventory(s.manager);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("status")).isEqualTo("CONCLUIDO");
    // Depois de finalizado, nada mais muda: nem marcar, nem desfazer.
    assertThat(s.revert(s.manager, s.second)).isEqualTo(HttpStatus.CONFLICT);
    assertThat(s.stored(s.second).validationStatus())
        .isEqualTo(Item.ValidationStatus.NAO_LOCALIZADO);
  }

  @Test
  void aNotFoundItemUndoneBlocksTheClosingAgain() {
    Scenario s = new Scenario();
    assertThat(s.validate(s.operator, s.first)).isEqualTo(HttpStatus.OK);
    assertThat(s.markNotFound(s.operator, s.second, REASON)).isEqualTo(HttpStatus.OK);
    assertThat(s.revert(s.manager, s.second)).isEqualTo(HttpStatus.OK);

    var response = s.finalizeInventory(s.manager);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(((Number) response.getBody().get("pendingItems")).longValue()).isEqualTo(1);
  }

  @Test
  void cannotFinalizeAnInventoryWithoutItems() {
    Sector sector = sectors.save(new Sector("Vazio " + System.nanoTime(), null));
    User admin = access.user("admin-" + System.nanoTime() + "@ifpe.edu.br", TestAccess.ADMIN);
    Inventory empty =
        inventories.save(
            new Inventory(
                "Vazio " + System.nanoTime(), "", LocalDate.now().plusDays(5), admin, sector));

    var response = call(admin, HttpMethod.POST, "/inventory/" + empty.id() + "/finalize", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(((Number) response.getBody().get("pendingItems")).longValue()).isZero();
    assertThat(response.getBody().get("message").toString()).contains("não tem itens");
    assertThat(inventories.findById(empty.id()).orElseThrow().status())
        .isEqualTo(Inventory.Status.EM_ANDAMENTO);
  }

  @Test
  void finalizingTwiceStaysAConflictNotAnotherRuleError() {
    Scenario s = new Scenario();
    assertThat(s.validate(s.operator, s.first)).isEqualTo(HttpStatus.OK);
    assertThat(s.validate(s.operator, s.second)).isEqualTo(HttpStatus.OK);
    assertThat(s.finalizeInventory(s.manager).getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(s.finalizeInventory(s.manager).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void permissionIsCheckedBeforeTheRule() {
    Scenario s = new Scenario();

    // Com itens pendentes, quem não pode finalizar recebe 403 e não o detalhe da pendência.
    assertThat(s.finalizeInventory(s.operator).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(s.finalizeInventory(s.consult).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
