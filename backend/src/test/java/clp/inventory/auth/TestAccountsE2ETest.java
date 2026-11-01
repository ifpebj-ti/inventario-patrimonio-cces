package clp.inventory.auth;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.dto.UserDto;
import clp.inventory.model.Inventory;
import clp.inventory.model.Item;
import clp.inventory.model.Sector;
import clp.inventory.model.User;
import clp.inventory.repository.InventoryRepository;
import clp.inventory.repository.ItemRepository;
import clp.inventory.repository.PermissionRepository;
import clp.inventory.repository.RoleRepository;
import clp.inventory.repository.SectorAllocationRepository;
import clp.inventory.repository.SectorRepository;
import clp.inventory.repository.UserRepository;
import clp.inventory.service.auth.TestAccountsInitializer;
import clp.inventory.testsupport.TestAccess;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sobe a aplicação com as três contas de teste ligadas (uma senha por conta) e confere o login por
 * usuário e senha, o cargo de cada uma no Setor de Testes, o que cada permissão faz de fato e a
 * idempotência do initializer.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class TestAccountsE2ETest {

  private static final String VIEWER_PASSWORD = "senha-teste-consulta-1";
  private static final String OPERATOR_PASSWORD = "senha-teste-operador-1";
  private static final String MANAGER_PASSWORD = "senha-teste-gestor-1";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("security.token.secret", () -> "test-secret-de-pelo-menos-32-bytes-aqui");
    registry.add("test.accounts.viewer.password", () -> VIEWER_PASSWORD);
    registry.add("test.accounts.operator.password", () -> OPERATOR_PASSWORD);
    registry.add("test.accounts.manager.password", () -> MANAGER_PASSWORD);
  }

  @Autowired TestRestTemplate http;
  @Autowired TestAccess access;
  @Autowired UserRepository users;
  @Autowired RoleRepository roles;
  @Autowired PermissionRepository permissions;
  @Autowired SectorRepository sectors;
  @Autowired SectorAllocationRepository allocations;
  @Autowired InventoryRepository inventories;
  @Autowired ItemRepository items;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired TestAccountsInitializer initializer;

  private TestAccountsInitializer initializer(String viewer, String operator, String manager) {
    return new TestAccountsInitializer(
        users,
        roles,
        permissions,
        sectors,
        allocations,
        inventories,
        passwordEncoder,
        viewer,
        operator,
        manager);
  }

  // Respostas de erro do login (401, 429) são texto, não JSON: por isso String aqui.
  private ResponseEntity<String> login(String username, String password) {
    return http.postForEntity(
        "/auth/login", Map.of("username", username, "password", password), String.class);
  }

  private HttpHeaders bearer(String username, String password) {
    var response =
        http.postForEntity(
            "/auth/login", Map.of("username", username, "password", password), Map.class);
    assertThat(response.getStatusCode()).as(username).isEqualTo(HttpStatus.OK);
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth((String) response.getBody().get("token"));
    return headers;
  }

  private HttpStatus status(HttpHeaders headers, HttpMethod method, String url, Object body) {
    return HttpStatus.valueOf(
        http.exchange(url, method, new HttpEntity<>(body, headers), String.class)
            .getStatusCode()
            .value());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> me(HttpHeaders headers) {
    return http.exchange("/auth/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class)
        .getBody();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> onlyAllocation(Map<String, Object> me) {
    var allocationsOfUser = (List<Map<String, Object>>) me.get("allocations");
    assertThat(allocationsOfUser).hasSize(1);
    return allocationsOfUser.get(0);
  }

  private Sector testSector() {
    return sectors.findByName(TestAccountsInitializer.SECTOR_NAME).orElseThrow();
  }

  private Inventory sampleInventory() {
    return inventories.findBySector_Id(testSector().id()).stream()
        .filter(inventory -> inventory.name().equals(TestAccountsInitializer.INVENTORY_NAME))
        .findFirst()
        .orElseThrow();
  }

  private Item firstItem(Inventory inventory) {
    return items.findByInventory_Id(inventory.id(), PageRequest.of(0, 10)).getContent().get(0);
  }

  private Map<String, Object> itemBody(Item item, boolean valid, String description) {
    Map<String, Object> body = new HashMap<>();
    body.put("id", item.id());
    body.put("code", item.code());
    body.put("description", description);
    body.put("responsible", item.responsible());
    // O mesmo preço do item: mandar outro conta como edição de dados.
    body.put(
        "price",
        String.format(java.util.Locale.forLanguageTag("pt-BR"), "R$ %,.2f", item.price() / 100.0));
    body.put("locale", item.locale());
    body.put("isValid", valid);
    body.put("qrCode", item.qrCode());
    body.put("observations", List.of());
    return body;
  }

  @Test
  void eachAccountLogsInWithItsOwnPasswordAndNotWithTheOthers() {
    assertThat(login(TestAccountsInitializer.VIEWER_LOGIN, VIEWER_PASSWORD).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(login(TestAccountsInitializer.OPERATOR_LOGIN, OPERATOR_PASSWORD).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(login(TestAccountsInitializer.MANAGER_LOGIN, MANAGER_PASSWORD).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    // O usuário não diferencia maiúsculas, e a senha de uma conta não abre a de outra.
    assertThat(login("TESTE.Gestor", MANAGER_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(login(TestAccountsInitializer.MANAGER_LOGIN, VIEWER_PASSWORD).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(login(TestAccountsInitializer.VIEWER_LOGIN, "senha-errada-qualquer").getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @SuppressWarnings("unchecked")
  void eachAccountHasExactlyItsPermissionInTheTestSector() {
    var viewer = me(bearer(TestAccountsInitializer.VIEWER_LOGIN, VIEWER_PASSWORD));
    var operator = me(bearer(TestAccountsInitializer.OPERATOR_LOGIN, OPERATOR_PASSWORD));
    var manager = me(bearer(TestAccountsInitializer.MANAGER_LOGIN, MANAGER_PASSWORD));

    for (var session : List.of(viewer, operator, manager)) {
      assertThat(session).containsEntry("administrator", false);
      assertThat(session.keySet()).doesNotContain("systemUser", "passwordHash", "googleId");
      assertThat(onlyAllocation(session)).containsEntry("sectorId", (int) testSector().id());
    }
    assertThat((List<String>) onlyAllocation(viewer).get("permissions"))
        .containsExactly("VIEW_SECTOR");
    assertThat((List<String>) onlyAllocation(operator).get("permissions"))
        .containsExactlyInAnyOrder("MANAGE_ITEM", "VIEW_SECTOR");
    assertThat((List<String>) onlyAllocation(manager).get("permissions"))
        .containsExactlyInAnyOrder("MANAGE_SECTOR", "VIEW_SECTOR");
    assertThat(onlyAllocation(viewer)).containsEntry("roleName", "Teste · Consulta");
  }

  @Test
  void viewerOnlyReadsTheSampleInventory() {
    var headers = bearer(TestAccountsInitializer.VIEWER_LOGIN, VIEWER_PASSWORD);
    Inventory inventory = sampleInventory();
    Item item = firstItem(inventory);

    assertThat(status(headers, HttpMethod.GET, "/inventory/" + inventory.id(), null))
        .isEqualTo(HttpStatus.OK);
    assertThat(
            status(
                headers,
                HttpMethod.GET,
                "/inventory/inventory-items?inventoryId=" + inventory.id() + "&page=0&pageSize=10",
                null))
        .isEqualTo(HttpStatus.OK);
    assertThat(status(headers, HttpMethod.GET, "/item/" + item.id(), null))
        .isEqualTo(HttpStatus.OK);
    assertThat(status(headers, HttpMethod.PUT, "/item/" + item.id(), itemBody(item, true, "Mesa")))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            status(
                headers,
                HttpMethod.PATCH,
                "/item/" + item.id() + "/notes",
                List.of(Map.of("content", "Observação"))))
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void operatorValidatesItemsButCannotEditTheirDataOrManageInventories() {
    var headers = bearer(TestAccountsInitializer.OPERATOR_LOGIN, OPERATOR_PASSWORD);
    Inventory inventory = sampleInventory();
    Item item = firstItem(inventory);

    assertThat(
            status(
                headers,
                HttpMethod.PUT,
                "/item/" + item.id(),
                itemBody(item, true, item.description())))
        .isEqualTo(HttpStatus.OK);
    assertThat(items.findById(item.id()).orElseThrow().isValid()).isTrue();
    assertThat(
            status(
                headers,
                HttpMethod.PUT,
                "/item/" + item.id(),
                itemBody(item, true, "Descrição alterada")))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(status(headers, HttpMethod.POST, "/inventory/" + inventory.id() + "/finalize", null))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            status(
                headers, HttpMethod.POST, "/inventory/new-inventory", newInventory(testSector())))
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void managerManagesOnlyTheTestSector() {
    var headers = bearer(TestAccountsInitializer.MANAGER_LOGIN, MANAGER_PASSWORD);
    Inventory inventory = sampleInventory();
    Item item = firstItem(inventory);
    Sector other = sectors.save(new Sector("Outro setor " + System.nanoTime(), null));
    Inventory otherInventory =
        inventories.save(
            new Inventory(
                "Outro inventário",
                "",
                LocalDate.now().plusDays(5),
                users.findByEmail(TestAccountsInitializer.MANAGER_LOGIN).orElseThrow(),
                other));

    assertThat(
            status(
                headers,
                HttpMethod.PUT,
                "/item/" + item.id(),
                itemBody(item, false, "Editado pelo gestor de teste")))
        .isEqualTo(HttpStatus.OK);
    assertThat(
            status(
                headers, HttpMethod.POST, "/inventory/new-inventory", newInventory(testSector())))
        .isEqualTo(HttpStatus.CREATED);
    assertThat(status(headers, HttpMethod.POST, "/inventory/new-inventory", newInventory(other)))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(status(headers, HttpMethod.GET, "/inventory/" + otherInventory.id(), null))
        .isEqualTo(HttpStatus.FORBIDDEN);
    // Gerencia as pessoas do setor de testes (a lista de membros e de candidatos abre).
    assertThat(status(headers, HttpMethod.GET, "/sectors/" + testSector().id() + "/users", null))
        .isEqualTo(HttpStatus.OK);
    assertThat(
            status(
                headers,
                HttpMethod.GET,
                "/sectors/" + testSector().id() + "/assignable-users",
                null))
        .isEqualTo(HttpStatus.OK);
    assertThat(status(headers, HttpMethod.GET, "/sectors/" + other.id() + "/users", null))
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  private Map<String, Object> newInventory(Sector sector) {
    Map<String, Object> body = new HashMap<>();
    body.put("name", "Novo inventário " + System.nanoTime());
    body.put("description", "");
    body.put("plannedEndDate", LocalDate.now().plusDays(10).toString());
    body.put("sectorId", sector.id());
    return body;
  }

  @Test
  void testAccountsAreHiddenFromTheUserAndMemberLists() {
    User admin = access.admin("admin-" + System.nanoTime() + "@ifpe.edu.br");
    var headers = new HttpHeaders();
    headers.setBearerAuth(
        com.auth0
            .jwt
            .JWT
            .create()
            .withIssuer("inventory")
            .withSubject(admin.getId().toString())
            .withExpiresAt(java.time.Instant.now().plusSeconds(3600))
            .sign(
                com.auth0.jwt.algorithms.Algorithm.HMAC256(
                    "test-secret-de-pelo-menos-32-bytes-aqui")));

    var listed =
        List.of(
            http.exchange("/users", HttpMethod.GET, new HttpEntity<>(headers), UserDto[].class)
                .getBody());
    var members =
        List.of(
            http.exchange(
                    "/sectors/" + testSector().id() + "/users",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    UserDto[].class)
                .getBody());

    assertThat(listed)
        .extracting(UserDto::email)
        .doesNotContain(
            TestAccountsInitializer.VIEWER_LOGIN,
            TestAccountsInitializer.OPERATOR_LOGIN,
            TestAccountsInitializer.MANAGER_LOGIN);
    assertThat(members).isEmpty();
  }

  @Test
  void runningTheInitializerAgainDoesNotDuplicateAnything() {
    long sectorsBefore = sectors.count();
    long rolesBefore = roles.count();
    long allocationsBefore = allocations.count();
    long inventoriesBefore = inventories.count();
    long usersBefore = users.count();

    initializer.run(null);
    initializer.run(null);

    assertThat(sectors.count()).isEqualTo(sectorsBefore);
    assertThat(roles.count()).isEqualTo(rolesBefore);
    assertThat(allocations.count()).isEqualTo(allocationsBefore);
    assertThat(inventories.count()).isEqualTo(inventoriesBefore);
    assertThat(users.count()).isEqualTo(usersBefore);
    assertThat(
            inventories.findBySector_Id(testSector().id()).stream()
                .filter(i -> i.name().equals(TestAccountsInitializer.INVENTORY_NAME)))
        .hasSize(1);
  }

  @Test
  void aNewPasswordRotatesTheHashAndAnEmptyOneTurnsOnlyThatLoginOff() {
    try {
      // Troca a senha do operador: a antiga deixa de valer, a nova passa a valer.
      initializer(VIEWER_PASSWORD, "outra-senha-operador-2", MANAGER_PASSWORD).run(null);
      assertThat(login(TestAccountsInitializer.OPERATOR_LOGIN, OPERATOR_PASSWORD).getStatusCode())
          .isEqualTo(HttpStatus.UNAUTHORIZED);
      assertThat(
              login(TestAccountsInitializer.OPERATOR_LOGIN, "outra-senha-operador-2")
                  .getStatusCode())
          .isEqualTo(HttpStatus.OK);

      // Senha vazia desliga só o login do gestor; a conta e a alocação ficam, e as outras seguem.
      initializer(VIEWER_PASSWORD, "outra-senha-operador-2", "").run(null);
      assertThat(login(TestAccountsInitializer.MANAGER_LOGIN, MANAGER_PASSWORD).getStatusCode())
          .isEqualTo(HttpStatus.UNAUTHORIZED);
      User manager = users.findByEmail(TestAccountsInitializer.MANAGER_LOGIN).orElseThrow();
      assertThat(manager.getPasswordHash()).isNull();
      assertThat(allocations.findByUser_Id(manager.getId())).hasSize(1);
      assertThat(login(TestAccountsInitializer.VIEWER_LOGIN, VIEWER_PASSWORD).getStatusCode())
          .isEqualTo(HttpStatus.OK);
    } finally {
      // Volta ao estado original para não interferir nos outros testes da classe.
      initializer(VIEWER_PASSWORD, OPERATOR_PASSWORD, MANAGER_PASSWORD).run(null);
    }
    assertThat(login(TestAccountsInitializer.MANAGER_LOGIN, MANAGER_PASSWORD).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void aTooShortPasswordChangesNothing() {
    try {
      initializer("curta", OPERATOR_PASSWORD, MANAGER_PASSWORD).run(null);

      // A conta existente segue com a senha de antes: a curta não a altera nem a derruba.
      assertThat(login(TestAccountsInitializer.VIEWER_LOGIN, "curta").getStatusCode())
          .isEqualTo(HttpStatus.UNAUTHORIZED);
      assertThat(login(TestAccountsInitializer.VIEWER_LOGIN, VIEWER_PASSWORD).getStatusCode())
          .isEqualTo(HttpStatus.OK);
    } finally {
      initializer(VIEWER_PASSWORD, OPERATOR_PASSWORD, MANAGER_PASSWORD).run(null);
    }
  }
}
