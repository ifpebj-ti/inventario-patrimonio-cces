package clp.inventory.itemaudit;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.model.Inventory;
import clp.inventory.model.Item;
import clp.inventory.model.Sector;
import clp.inventory.model.User;
import clp.inventory.repository.InventoryRepository;
import clp.inventory.repository.ItemRepository;
import clp.inventory.repository.SectorRepository;
import clp.inventory.testsupport.TestAccess;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Item com histórico não é mais excluído (FIN-09): quem nunca sofreu ação pode ser removido, quem
 * já foi validado, desvalidado ou marcado como não localizado fica. A limpeza do inventário remove
 * só os itens sem histórico, a reimportação convive com os que ficaram e a linha que os repete é
 * recusada. Postgres real via Testcontainers e HTTP real.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ItemHistoryDeletionE2ETest {

  private static final String TEST_SECRET = "test-secret-de-pelo-menos-32-bytes-aqui";
  private static final String HEADER = "Código;Descrição;Responsável;Valor;Sala;Observação\n";

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

  /** Setor com gestor e operador e um inventário aberto com quatro itens, todos sem histórico. */
  private final class Scenario {
    final Sector sector = sectors.save(new Sector("Setor " + System.nanoTime(), null));
    final User manager = member(TestAccess.MANAGER);
    final User operator = member(TestAccess.OPERATOR);
    final Inventory inventory;
    final List<Item> all;

    Scenario() {
      Inventory created =
          new Inventory(
              "Inventario " + System.nanoTime(), "", LocalDate.now().plusDays(30), manager, sector);
      for (int i = 1; i <= 4; i++) {
        created.addItem(
            new Item("I" + i + "-" + System.nanoTime(), "", "Bem " + i, 1000, null, "Eu"));
      }
      inventory = inventories.save(created);
      all = inventory.items();
    }

    private User member(String role) {
      User user = access.user("u-" + System.nanoTime() + "@ifpe.edu.br", role);
      access.allocate(user, sector);
      return user;
    }

    Item item(int index) {
      return all.get(index);
    }

    long id() {
      return inventory.id();
    }

    long count() {
      return items.countByInventory_Id(inventory.id());
    }

    HttpStatus validate(Item item) {
      return status(operator, HttpMethod.PATCH, "/item/" + item.id() + "/validate", null);
    }

    HttpStatus revert(Item item) {
      return status(
          manager, HttpMethod.PATCH, "/item/" + item.id() + "/validate", Map.of("isValid", false));
    }

    HttpStatus markNotFound(Item item) {
      return status(
          operator,
          HttpMethod.PATCH,
          "/item/" + item.id() + "/not-found",
          Map.of("reason", "Não encontrado na sala indicada."));
    }

    ResponseEntity<Map> deleteItem(User user, Item item) {
      return call(
          user,
          HttpMethod.DELETE,
          "/inventory/delete-item?inventoryId=" + id() + "&itemId=" + item.id());
    }

    ResponseEntity<Map> clear(User user) {
      return call(user, HttpMethod.DELETE, "/inventory/clear-items?inventoryId=" + id());
    }
  }

  private HttpHeaders headers(User user) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(
        JWT.create()
            .withIssuer("inventory")
            .withJWTId(UUID.randomUUID().toString())
            .withClaim("ver", 0)
            .withSubject(String.valueOf(user.getId()))
            .withExpiresAt(Instant.now().plus(Duration.ofHours(1)))
            .sign(Algorithm.HMAC256(TEST_SECRET)));
    return headers;
  }

  private ResponseEntity<Map> call(User user, HttpMethod method, String url) {
    return http.exchange(url, method, new HttpEntity<>(null, headers(user)), Map.class);
  }

  private HttpStatus status(User user, HttpMethod method, String url, Object body) {
    return HttpStatus.valueOf(
        http.exchange(url, method, new HttpEntity<>(body, headers(user)), String.class)
            .getStatusCode()
            .value());
  }

  // inventoryId nulo: a planilha vai sem inventário de destino.
  private ResponseEntity<Map> postCsv(User user, String url, Long inventoryId, String csv) {
    HttpHeaders headers = headers(user);
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    if (inventoryId != null) {
      body.add("inventoryId", String.valueOf(inventoryId));
    }
    body.add(
        "file",
        new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
          @Override
          public String getFilename() {
            return "patrimonio.csv";
          }
        });
    return http.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
  }

  private ResponseEntity<Map> importCsv(User user, long inventoryId, String csv) {
    return postCsv(user, "/inventory/add-items", inventoryId, csv);
  }

  // ------------------------------------------------------------------ excluir um item

  @Test
  void itemWithoutHistoryCanBeDeleted() {
    Scenario s = new Scenario();

    var response = s.deleteItem(s.manager, s.item(0));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(s.count()).isEqualTo(3);
  }

  @Test
  void validatedItemCannotBeDeletedAndTheAnswerSaysWhy() {
    Scenario s = new Scenario();
    assertThat(s.validate(s.item(0))).isEqualTo(HttpStatus.OK);

    var response = s.deleteItem(s.manager, s.item(0));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().get("detail").toString()).contains("histórico de validação");
    assertThat(items.existsById(s.item(0).id())).isTrue();
  }

  @Test
  void revertedItemIsPendingAgainButKeepsItsHistory() {
    Scenario s = new Scenario();
    s.validate(s.item(0));
    assertThat(s.revert(s.item(0))).isEqualTo(HttpStatus.OK);

    var response = s.deleteItem(s.manager, s.item(0));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(items.existsById(s.item(0).id())).isTrue();
  }

  @Test
  void itemMarkedAsNotFoundCannotBeDeleted() {
    Scenario s = new Scenario();
    s.markNotFound(s.item(0));

    assertThat(s.deleteItem(s.manager, s.item(0)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  // ------------------------------------------------------------------ limpar o inventário

  @Test
  void clearRemovesEveryItemWhenNoneHasHistory() {
    Scenario s = new Scenario();

    var response = s.clear(s.manager);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("removed", 4).containsEntry("kept", 0);
    assertThat(s.count()).isZero();
  }

  @Test
  void clearKeepsTheItemsThatAlreadyHaveHistoryAndRemovesTheOthers() {
    Scenario s = new Scenario();
    s.validate(s.item(0));
    s.markNotFound(s.item(1));
    s.validate(s.item(2));
    s.revert(s.item(2));

    var response = s.clear(s.manager);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("removed", 1).containsEntry("kept", 3);
    assertThat(response.getBody().get("message").toString()).contains("3 itens permaneceram");
    assertThat(items.existsById(s.item(0).id())).isTrue();
    assertThat(items.existsById(s.item(1).id())).isTrue();
    assertThat(items.existsById(s.item(2).id())).isTrue();
    assertThat(items.existsById(s.item(3).id())).isFalse();
  }

  @Test
  void clearSaysSingularWhenOneItemStays() {
    Scenario s = new Scenario();
    s.validate(s.item(0));

    var response = s.clear(s.manager);

    assertThat(response.getBody()).containsEntry("removed", 3).containsEntry("kept", 1);
    assertThat(response.getBody().get("message").toString()).contains("1 item permaneceu");
  }

  @Test
  void clearIsRepeatableAndTheSecondRunRemovesNothing() {
    Scenario s = new Scenario();
    s.validate(s.item(0));
    s.clear(s.manager);

    var again = s.clear(s.manager);

    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(again.getBody()).containsEntry("removed", 0).containsEntry("kept", 1);
  }

  @Test
  void clearIsStillRestrictedByRoleAndInventoryState() {
    Scenario s = new Scenario();

    assertThat(s.clear(s.operator).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(s.count()).isEqualTo(4);
  }

  // ------------------------------------------------------------------ reimportar

  @Test
  void importIsRefusedWhileThereAreItemsWithoutHistory() {
    Scenario s = new Scenario();
    s.validate(s.item(0));

    var response = importCsv(s.manager, s.id(), HEADER + "N-1;Novo;Eu;1,00;Sala;\n");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().get("detail").toString())
        .contains("3 itens sem conferência")
        .contains("exclua-os primeiro");
    assertThat(s.count()).isEqualTo(4);
  }

  @Test
  void afterClearingTheImportAddsTheNewItemsNextToTheOnesThatStayed() {
    Scenario s = new Scenario();
    s.validate(s.item(0));
    s.validate(s.item(1));
    s.clear(s.manager);
    assertThat(s.count()).isEqualTo(2);

    var response =
        importCsv(
            s.manager,
            s.id(),
            HEADER + "NOVO-1;Novo 1;Eu;1,00;Sala;\nNOVO-2;Novo 2;Eu;2,00;Sala;\n");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(s.count()).isEqualTo(4);
  }

  @Test
  void aSheetRowThatRepeatsAKeptItemIsRefusedAndNothingIsImported() {
    Scenario s = new Scenario();
    s.validate(s.item(0));
    s.clear(s.manager);
    String keptCode = s.item(0).code();

    var response =
        importCsv(
            s.manager,
            s.id(),
            HEADER + "NOVO-1;Novo 1;Eu;1,00;Sala;\n" + keptCode + ";Repetido;Eu;3,00;Sala;\n");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    String failed = response.getBody().get("failedItems\n").toString();
    assertThat(failed).contains(keptCode).contains("já consta no inventário");
    assertThat(failed).doesNotContain("NOVO-1");
    assertThat(s.count()).isEqualTo(1);
  }

  @Test
  void validateSheetFlagsTheRowsThatAlreadyExistInTheInventory() {
    Scenario s = new Scenario();
    s.validate(s.item(0));
    s.clear(s.manager);
    String csv = HEADER + s.item(0).code() + ";Repetido;Eu;3,00;Sala;\nNOVO-1;Novo;Eu;1,00;Sala;\n";

    var withInventory = postCsv(s.manager, "/inventory/validate-sheet", s.id(), csv);
    var withoutInventory = postCsv(s.manager, "/inventory/validate-sheet", null, csv);

    assertThat(withInventory.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(withInventory.getBody().get("errors").toString())
        .contains(s.item(0).code())
        .contains("já consta no inventário")
        .contains("line=2");
    // Sem o inventário não há com o que comparar.
    assertThat(withoutInventory.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void validateSheetRefusesWhatTheImportWouldRefuseWhileThereAreItemsWithoutHistory() {
    Scenario s = new Scenario();
    s.validate(s.item(0));

    var response =
        postCsv(
            s.manager, "/inventory/validate-sheet", s.id(), HEADER + "N-1;Novo;Eu;1,00;Sala;\n");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().get("detail").toString()).contains("3 itens sem conferência");
  }

  @Test
  void validateSheetDoesNotRevealTheItemsOfAnInventoryTheUserCannotManage() {
    Scenario s = new Scenario();
    Scenario other = new Scenario();
    String csv = HEADER + other.item(0).code() + ";Repetido;Eu;3,00;Sala;\n";

    var response = postCsv(s.manager, "/inventory/validate-sheet", other.id(), csv);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(String.valueOf(response.getBody())).doesNotContain(other.item(0).code());
  }

  // ------------------------------------------------------------------ excluir o inventário

  @Test
  void inventoryWithoutHistoryCanBeDeleted() {
    Scenario s = new Scenario();

    var response = call(s.manager, HttpMethod.DELETE, "/inventory/" + s.id());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(inventories.existsById(s.id())).isFalse();
  }

  @Test
  void inventoryWithAnItemThatHasHistoryCannotBeDeleted() {
    Scenario s = new Scenario();
    s.validate(s.item(0));

    var response = call(s.manager, HttpMethod.DELETE, "/inventory/" + s.id());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(inventories.existsById(s.id())).isTrue();
    assertThat(s.count()).isEqualTo(4);
  }
}
