package clp.inventory.access;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.dto.InventoryDto;
import clp.inventory.dto.SectorDto;
import clp.inventory.dto.UserDto;
import clp.inventory.model.Inventory;
import clp.inventory.model.Item;
import clp.inventory.model.Sector;
import clp.inventory.model.User;
import clp.inventory.repository.InventoryRepository;
import clp.inventory.repository.ItemRepository;
import clp.inventory.repository.SectorAllocationRepository;
import clp.inventory.repository.SectorRepository;
import clp.inventory.repository.UserRepository;
import clp.inventory.testsupport.TestAccess;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.nio.charset.StandardCharsets;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageRequest;
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
 * Controle de acesso por cargo, ponta a ponta (Postgres real e HTTP real).
 *
 * <ul>
 *   <li>Administrador (coluna is_administrator): vê e gerencia tudo.
 *   <li>Gestor de Setor: gerencia os inventários do seu setor e aloca só Operador e Consulta.
 *   <li>Operador de Campo: vê o setor, valida itens e registra observações; não gerencia
 *       inventário.
 *   <li>CONSULTA: somente leitura no seu setor.
 * </ul>
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.mail.password=test"})
@AutoConfigureTestRestTemplate
class AccessControlE2ETest {
  private static final String SECRET = "access-control-test-secret-32-bytes-long";
  private static final String CSV =
      "Código;Descrição;Responsável;Valor;Sala;Observações\n001;Monitor;TI;750,00;Sala 1;Bom\n";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("security.token.secret", () -> SECRET);
  }

  @Autowired TestRestTemplate http;
  @Autowired UserRepository users;
  @Autowired TestAccess access;
  @Autowired SectorRepository sectors;
  @Autowired SectorAllocationRepository allocations;
  @Autowired InventoryRepository inventories;
  @Autowired ItemRepository items;

  // ---------------------------------------------------------------- cenário e utilitários

  /** Dois setores, cada um com gestor, operador e consulta, e um inventário com itens em cada. */
  private class Scenario {
    final User admin = user(TestAccess.ADMIN);
    final Sector sectorA = sector("A");
    final Sector sectorB = sector("B");
    final User managerA = user(TestAccess.MANAGER, sectorA);
    final User operatorA = user(TestAccess.OPERATOR, sectorA);
    final User consultA = user(TestAccess.CONSULT, sectorA);
    final User managerB = user(TestAccess.MANAGER, sectorB);
    final Inventory inventoryA = inventory(managerA, sectorA);
    final Inventory inventoryB = inventory(managerB, sectorB);
    final Item itemA = firstItem(inventoryA);
    final Item itemB = firstItem(inventoryB);
  }

  private User user(String roleName, Sector... allocatedTo) {
    User saved = access.user("access-" + System.nanoTime() + "@ifpe.edu.br", roleName);
    for (Sector sector : allocatedTo) {
      access.allocate(saved, sector);
    }
    return saved;
  }

  private Sector sector(String label) {
    return sectors.save(new Sector("Acesso " + label + " " + System.nanoTime(), null));
  }

  private Inventory inventory(User owner, Sector sector) {
    Inventory inventory =
        new Inventory(
            "Acesso " + System.nanoTime(), "", LocalDate.now().plusDays(5), owner, sector);
    inventory.addItem(new Item("PAT" + System.nanoTime(), "", "Mesa", 1000, "Sala 1", "Maria"));
    return inventories.save(inventory);
  }

  private Item firstItem(Inventory inventory) {
    return items.findByInventory_Id(inventory.id(), PageRequest.of(0, 10)).getContent().get(0);
  }

  private long roleId(String name) {
    return access.role(name).id();
  }

  private HttpHeaders headers(User user) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(
        JWT.create()
            .withIssuer("inventory")
            .withSubject(user.getId().toString())
            .withExpiresAt(Instant.now().plusSeconds(3600))
            .sign(Algorithm.HMAC256(SECRET)));
    return headers;
  }

  private <T> ResponseEntity<T> call(
      User user, HttpMethod method, String url, Object body, Class<T> type) {
    return http.exchange(url, method, new HttpEntity<>(body, headers(user)), type);
  }

  private HttpStatus status(User user, HttpMethod method, String url, Object body) {
    return HttpStatus.valueOf(call(user, method, url, body, String.class).getStatusCode().value());
  }

  private Map<String, Object> itemBody(Item item, boolean valid, String description) {
    Map<String, Object> body = new HashMap<>();
    body.put("id", item.id());
    body.put("code", item.code());
    body.put("description", description);
    body.put("responsible", item.responsible());
    body.put("price", "R$ 10,00");
    body.put("locale", item.locale());
    body.put("isValid", valid);
    body.put("qrCode", item.qrCode());
    body.put("observations", List.of());
    return body;
  }

  private Map<String, Object> inventoryBody(Inventory inventory, Sector sector) {
    Map<String, Object> body = new HashMap<>();
    body.put("name", inventory.name());
    body.put("description", "");
    body.put("plannedEndDate", LocalDate.now().plusDays(10).toString());
    body.put("sectorId", sector.id());
    return body;
  }

  private ResponseEntity<String> importCsv(User user, long inventoryId) {
    HttpHeaders headers = headers(user);
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("inventoryId", String.valueOf(inventoryId));
    body.add(
        "file",
        new ByteArrayResource(CSV.getBytes(StandardCharsets.UTF_8)) {
          @Override
          public String getFilename() {
            return "patrimonio.csv";
          }
        });
    return http.exchange(
        "/inventory/add-items", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
  }

  // ---------------------------------------------------------------- inventários: leitura

  @Test
  void inventoryListIsScopedBySectorForEveryRole() {
    Scenario s = new Scenario();

    for (User user : List.of(s.managerA, s.operatorA, s.consultA)) {
      var ids =
          List.of(
                  call(
                          user,
                          HttpMethod.GET,
                          "/inventory/user-inventories",
                          null,
                          InventoryDto[].class)
                      .getBody())
              .stream()
              .map(InventoryDto::id)
              .toList();
      assertThat(ids).contains(s.inventoryA.id()).doesNotContain(s.inventoryB.id());
    }
    var managerB =
        call(s.managerB, HttpMethod.GET, "/inventory/user-inventories", null, InventoryDto[].class)
            .getBody();
    assertThat(List.of(managerB))
        .extracting(InventoryDto::id)
        .contains(s.inventoryB.id())
        .doesNotContain(s.inventoryA.id());
    var all =
        call(s.admin, HttpMethod.GET, "/inventory/user-inventories", null, InventoryDto[].class)
            .getBody();
    assertThat(List.of(all))
        .extracting(InventoryDto::id)
        .contains(s.inventoryA.id(), s.inventoryB.id());
  }

  @Test
  void inventoryDetailsItemsAndExportRequireTheSector() {
    Scenario s = new Scenario();
    String details = "/inventory/" + s.inventoryA.id();
    String listing =
        "/inventory/inventory-items?inventoryId=" + s.inventoryA.id() + "&page=0&pageSize=10";
    String export = "/item/export-sheet?inventoryId=" + s.inventoryA.id();
    String itemUrl = "/item/" + s.itemA.id();

    for (User user : List.of(s.admin, s.managerA, s.operatorA, s.consultA)) {
      assertThat(status(user, HttpMethod.GET, details, null)).isEqualTo(HttpStatus.OK);
      assertThat(status(user, HttpMethod.GET, listing, null)).isEqualTo(HttpStatus.OK);
      assertThat(status(user, HttpMethod.GET, export, null)).isEqualTo(HttpStatus.OK);
      assertThat(status(user, HttpMethod.GET, itemUrl, null)).isEqualTo(HttpStatus.OK);
    }
    // Outro setor não enxerga nada do inventário A.
    assertThat(status(s.managerB, HttpMethod.GET, details, null)).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(status(s.managerB, HttpMethod.GET, listing, null)).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(status(s.managerB, HttpMethod.GET, export, null)).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(status(s.managerB, HttpMethod.GET, itemUrl, null)).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void onlyManagersCanFinalizeAccordingToCanFinalizeFlag() {
    Scenario s = new Scenario();
    String details = "/inventory/" + s.inventoryA.id();

    assertThat(call(s.managerA, HttpMethod.GET, details, null, Map.class).getBody())
        .containsEntry("canFinalize", true);
    assertThat(call(s.operatorA, HttpMethod.GET, details, null, Map.class).getBody())
        .containsEntry("canFinalize", false);
    assertThat(call(s.consultA, HttpMethod.GET, details, null, Map.class).getBody())
        .containsEntry("canFinalize", false);
  }

  // ---------------------------------------------------------------- inventários: gestão

  @Test
  void onlyAdminAndSectorManagerCreateInventories() {
    Scenario s = new Scenario();
    Map<String, Object> body = inventoryBody(s.inventoryA, s.sectorA);
    java.util.function.Supplier<Map<String, Object>> fresh =
        () -> {
          Map<String, Object> copy = new HashMap<>(body);
          copy.put("name", "Novo " + System.nanoTime());
          return copy;
        };

    assertThat(status(s.operatorA, HttpMethod.POST, "/inventory/new-inventory", fresh.get()))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(status(s.consultA, HttpMethod.POST, "/inventory/new-inventory", fresh.get()))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(status(s.managerB, HttpMethod.POST, "/inventory/new-inventory", fresh.get()))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(status(s.managerA, HttpMethod.POST, "/inventory/new-inventory", fresh.get()))
        .isEqualTo(HttpStatus.CREATED);
    assertThat(status(s.admin, HttpMethod.POST, "/inventory/new-inventory", fresh.get()))
        .isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void operatorConsultAndOtherSectorManagerCannotEditFinalizeOrDeleteInventory() {
    Scenario s = new Scenario();
    String url = "/inventory/" + s.inventoryA.id();
    Map<String, Object> edit = inventoryBody(s.inventoryA, s.sectorA);

    for (User user : List.of(s.operatorA, s.consultA, s.managerB)) {
      assertThat(status(user, HttpMethod.PUT, url, edit)).isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(status(user, HttpMethod.POST, url + "/finalize", null))
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(status(user, HttpMethod.DELETE, url, null)).isEqualTo(HttpStatus.FORBIDDEN);
    }
    assertThat(inventories.findById(s.inventoryA.id())).isPresent();
    assertThat(status(s.managerA, HttpMethod.PUT, url, edit)).isEqualTo(HttpStatus.OK);
    assertThat(status(s.managerA, HttpMethod.POST, url + "/finalize", null))
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void managerOfTheSectorCanDeleteItsInventory() {
    Scenario s = new Scenario();
    assertThat(status(s.managerA, HttpMethod.DELETE, "/inventory/" + s.inventoryA.id(), null))
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(inventories.findById(s.inventoryA.id())).isEmpty();
  }

  // ---------------------------------------------------------------- itens

  @Test
  void operatorValidatesItemsButCannotChangeItemData() {
    Scenario s = new Scenario();
    String url = "/item/" + s.itemA.id();

    // Validar (somente isValid muda) é a operação de campo.
    assertThat(status(s.operatorA, HttpMethod.PUT, url, itemBody(s.itemA, true, "Mesa")))
        .isEqualTo(HttpStatus.OK);
    assertThat(items.findById(s.itemA.id()).orElseThrow().isValid()).isTrue();
    // Editar dados do item é do gestor/admin.
    assertThat(status(s.operatorA, HttpMethod.PUT, url, itemBody(s.itemA, true, "Outra")))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(items.findById(s.itemA.id()).orElseThrow().description()).isEqualTo("Mesa");
    assertThat(status(s.managerA, HttpMethod.PUT, url, itemBody(s.itemA, true, "Outra")))
        .isEqualTo(HttpStatus.OK);
    assertThat(items.findById(s.itemA.id()).orElseThrow().description()).isEqualTo("Outra");
  }

  @Test
  void consultAndOtherSectorUsersCannotValidateOrAnnotateItems() {
    Scenario s = new Scenario();
    String url = "/item/" + s.itemA.id();
    List<Map<String, String>> notes = List.of(Map.of("content", "Observação"));

    for (User user : List.of(s.consultA, s.managerB)) {
      assertThat(status(user, HttpMethod.PUT, url, itemBody(s.itemA, true, "Mesa")))
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(status(user, HttpMethod.PATCH, url + "/notes", notes))
          .isEqualTo(HttpStatus.FORBIDDEN);
    }
    assertThat(items.findById(s.itemA.id()).orElseThrow().isValid()).isFalse();
    assertThat(status(s.operatorA, HttpMethod.PATCH, url + "/notes", notes))
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void finalizedInventoryBlocksValidationAndStructureChanges() {
    Scenario s = new Scenario();
    String url = "/item/" + s.itemA.id();
    assertThat(
            status(
                s.managerA, HttpMethod.POST, "/inventory/" + s.inventoryA.id() + "/finalize", null))
        .isEqualTo(HttpStatus.OK);

    // Escanear/validar depois de finalizar não é permitido.
    assertThat(status(s.operatorA, HttpMethod.PUT, url, itemBody(s.itemA, true, "Mesa")))
        .isEqualTo(HttpStatus.CONFLICT);
    // Importar, limpar, remover e incluir item também não.
    assertThat(importCsv(s.managerA, s.inventoryA.id()).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            status(
                s.managerA,
                HttpMethod.DELETE,
                "/inventory/clear-items?inventoryId=" + s.inventoryA.id(),
                null))
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            status(
                s.managerA,
                HttpMethod.DELETE,
                "/inventory/delete-item?inventoryId="
                    + s.inventoryA.id()
                    + "&itemId="
                    + s.itemA.id(),
                null))
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(items.findByInventory_Id(s.inventoryA.id(), PageRequest.of(0, 10)).getContent())
        .hasSize(1);
  }

  // ---------------------------------------------------------------- importação

  @Test
  void importIsManagerOnlyAndAllowedOnlyOncePerInventory() {
    Scenario s = new Scenario();
    Inventory empty =
        inventories.save(
            new Inventory(
                "Vazio " + System.nanoTime(),
                "",
                LocalDate.now().plusDays(5),
                s.managerA,
                s.sectorA));
    long id = empty.id();

    assertThat(importCsv(s.operatorA, id).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(importCsv(s.consultA, id).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(importCsv(s.managerB, id).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(importCsv(s.managerA, id).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    // Já existe uma importação: é preciso limpar os itens antes de importar outra planilha.
    assertThat(importCsv(s.managerA, id).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            status(
                s.operatorA, HttpMethod.DELETE, "/inventory/clear-items?inventoryId=" + id, null))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            status(s.managerA, HttpMethod.DELETE, "/inventory/clear-items?inventoryId=" + id, null))
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(importCsv(s.managerA, id).getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  // ---------------------------------------------------------------- setores

  @Test
  void sectorsAreListedByAllocationAndMembersOnlyForManagers() {
    Scenario s = new Scenario();

    for (User user : List.of(s.managerA, s.operatorA, s.consultA)) {
      var listed =
          List.of(call(user, HttpMethod.GET, "/sectors", null, SectorDto[].class).getBody());
      assertThat(listed).extracting(SectorDto::id).containsExactly(s.sectorA.id());
    }
    assertThat(
            List.of(call(s.admin, HttpMethod.GET, "/sectors", null, SectorDto[].class).getBody()))
        .extracting(SectorDto::id)
        .contains(s.sectorA.id(), s.sectorB.id());

    String members = "/sectors/" + s.sectorA.id() + "/users";
    assertThat(status(s.managerA, HttpMethod.GET, members, null)).isEqualTo(HttpStatus.OK);
    assertThat(status(s.operatorA, HttpMethod.GET, members, null)).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(status(s.consultA, HttpMethod.GET, members, null)).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(status(s.managerB, HttpMethod.GET, members, null)).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void onlyAdminCreatesDeactivatesAndDeletesSectors() {
    Scenario s = new Scenario();
    Map<String, Object> body = Map.of("name", "Novo setor " + System.nanoTime());

    for (User user : List.of(s.managerA, s.operatorA, s.consultA)) {
      assertThat(status(user, HttpMethod.POST, "/sectors", body)).isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(status(user, HttpMethod.PATCH, "/sectors/" + s.sectorA.id() + "/deactivate", null))
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(status(user, HttpMethod.DELETE, "/sectors/" + s.sectorA.id(), null))
          .isEqualTo(HttpStatus.FORBIDDEN);
    }
    assertThat(status(s.admin, HttpMethod.POST, "/sectors", body)).isEqualTo(HttpStatus.CREATED);
    // O setor A tem gente alocada: nem o admin o exclui.
    assertThat(status(s.admin, HttpMethod.DELETE, "/sectors/" + s.sectorA.id(), null))
        .isEqualTo(HttpStatus.CONFLICT);
  }

  // ---------------------------------------------------------------- usuários

  @Test
  void managerListsOnlyUsersOfOwnSectorsAndOthersCannotList() {
    Scenario s = new Scenario();

    var listed =
        List.of(call(s.managerA, HttpMethod.GET, "/users", null, UserDto[].class).getBody());
    assertThat(listed)
        .extracting(UserDto::id)
        .contains(s.operatorA.getId(), s.consultA.getId())
        .doesNotContain(s.managerB.getId());
    assertThat(status(s.operatorA, HttpMethod.GET, "/users", null)).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(status(s.consultA, HttpMethod.GET, "/users", null)).isEqualTo(HttpStatus.FORBIDDEN);
    var all = List.of(call(s.admin, HttpMethod.GET, "/users", null, UserDto[].class).getBody());
    assertThat(all).extracting(UserDto::id).contains(s.managerA.getId(), s.managerB.getId());
  }

  @Test
  void usersCannotBeCreatedOrDeletedThroughTheApi() {
    Scenario s = new Scenario();
    long before = users.count();
    Map<String, Object> body =
        Map.of("email", "novo-" + System.nanoTime() + "@ifpe.edu.br", "name", "Novo");

    // Usuário só nasce no primeiro login pelo Google; nem o admin cria por aqui.
    for (User actor : List.of(s.admin, s.managerA)) {
      assertThat(status(actor, HttpMethod.POST, "/users", body))
          .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }
    // E nunca é apagado: só desativado.
    for (User actor : List.of(s.admin, s.managerA)) {
      assertThat(
              status(actor, HttpMethod.DELETE, "/users/" + s.operatorA.getId(), null)
                  .is2xxSuccessful())
          .isFalse();
    }
    assertThat(users.count()).isEqualTo(before);
    assertThat(users.findById(s.operatorA.getId())).isPresent();
  }

  private HttpStatus setRole(User actor, User target, Sector sector, String roleName) {
    return status(
        actor,
        HttpMethod.PUT,
        "/users/" + target.getId() + "/sectors/" + sector.id(),
        Map.of("roleId", roleId(roleName)));
  }

  private String roleIn(User user, Sector sector) {
    return allocations
        .findByUser_IdAndSector_Id(user.getId(), sector.id())
        .orElseThrow()
        .role()
        .name();
  }

  @Test
  void managerSetsRolesOnlyWithoutManagementInOwnSector() {
    Scenario s = new Scenario();

    assertThat(setRole(s.managerA, s.operatorA, s.sectorA, TestAccess.CONSULT))
        .isEqualTo(HttpStatus.OK);
    assertThat(roleIn(s.operatorA, s.sectorA)).isEqualTo(TestAccess.CONSULT);
    // Promover a gestor é papel do administrador.
    assertThat(setRole(s.managerA, s.operatorA, s.sectorA, TestAccess.MANAGER))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(roleIn(s.operatorA, s.sectorA)).isEqualTo(TestAccess.CONSULT);
    // Gestor de outro setor não tem alcance sobre o setor A, e o gestor A não tem sobre o B.
    assertThat(setRole(s.managerB, s.operatorA, s.sectorA, TestAccess.OPERATOR))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(setRole(s.managerA, s.managerB, s.sectorB, TestAccess.CONSULT))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(roleIn(s.managerB, s.sectorB)).isEqualTo(TestAccess.MANAGER);
    // Operador e consulta não alteram cargos.
    for (User user : List.of(s.operatorA, s.consultA)) {
      assertThat(setRole(user, s.consultA, s.sectorA, TestAccess.OPERATOR))
          .isEqualTo(HttpStatus.FORBIDDEN);
    }
    // O administrador troca qualquer cargo, inclusive para gestor.
    assertThat(setRole(s.admin, s.operatorA, s.sectorA, TestAccess.MANAGER))
        .isEqualTo(HttpStatus.OK);
    assertThat(roleIn(s.operatorA, s.sectorA)).isEqualTo(TestAccess.MANAGER);
  }

  @Test
  void managerCannotDemoteAnotherManagerOfTheSector() {
    Scenario s = new Scenario();
    User secondManager = user(TestAccess.MANAGER, s.sectorA);

    assertThat(setRole(s.managerA, secondManager, s.sectorA, TestAccess.CONSULT))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            status(
                s.managerA,
                HttpMethod.DELETE,
                "/users/" + secondManager.getId() + "/sectors/" + s.sectorA.id(),
                null))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(roleIn(secondManager, s.sectorA)).isEqualTo(TestAccess.MANAGER);
  }

  @Test
  void allocationRequiresAnExistingRole() {
    Scenario s = new Scenario();
    User free = user(TestAccess.CONSULT);
    String url = "/users/" + free.getId() + "/sectors/" + s.sectorA.id();

    assertThat(status(s.managerA, HttpMethod.PUT, url, new HashMap<String, Object>()))
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(status(s.managerA, HttpMethod.PUT, url, Map.of("roleId", 999999)))
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(allocations.existsByUser_IdAndSector_Id(free.getId(), s.sectorA.id())).isFalse();
  }

  @Test
  void sameUserIsManagerInOneSectorAndOnlyOperatorInAnother() {
    Scenario s = new Scenario();
    User dual = user(TestAccess.CONSULT);
    access.allocate(dual, s.sectorA, TestAccess.MANAGER);
    access.allocate(dual, s.sectorB, TestAccess.OPERATOR);

    // No setor A ele gerencia inventários; no B só vê e valida itens.
    Map<String, Object> newInventoryA = inventoryBody(s.inventoryA, s.sectorA);
    newInventoryA.put("name", "Dual A " + System.nanoTime());
    Map<String, Object> newInventoryB = inventoryBody(s.inventoryB, s.sectorB);
    newInventoryB.put("name", "Dual B " + System.nanoTime());
    assertThat(status(dual, HttpMethod.POST, "/inventory/new-inventory", newInventoryA))
        .isEqualTo(HttpStatus.CREATED);
    assertThat(status(dual, HttpMethod.POST, "/inventory/new-inventory", newInventoryB))
        .isEqualTo(HttpStatus.FORBIDDEN);

    String urlB = "/inventory/" + s.inventoryB.id();
    assertThat(status(dual, HttpMethod.GET, urlB, null)).isEqualTo(HttpStatus.OK);
    assertThat(status(dual, HttpMethod.PUT, urlB, inventoryBody(s.inventoryB, s.sectorB)))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(status(dual, HttpMethod.POST, urlB + "/finalize", null))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(dual, HttpMethod.GET, "/inventory/" + s.inventoryA.id(), null, Map.class)
                .getBody())
        .containsEntry("canFinalize", true);
    assertThat(call(dual, HttpMethod.GET, urlB, null, Map.class).getBody())
        .containsEntry("canFinalize", false);

    String itemB = "/item/" + s.itemB.id();
    assertThat(status(dual, HttpMethod.PUT, itemB, itemBody(s.itemB, true, "Mesa")))
        .isEqualTo(HttpStatus.OK);
    assertThat(status(dual, HttpMethod.PUT, itemB, itemBody(s.itemB, true, "Outra")))
        .isEqualTo(HttpStatus.FORBIDDEN);
    String itemA = "/item/" + s.itemA.id();
    assertThat(status(dual, HttpMethod.PUT, itemA, itemBody(s.itemA, true, "Outra")))
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void managerAllocatesOnlyOperatorsAndConsultToOwnSector() {
    Scenario s = new Scenario();
    User free = user(TestAccess.CONSULT);

    assertThat(setRole(s.managerA, free, s.sectorA, TestAccess.OPERATOR)).isEqualTo(HttpStatus.OK);
    assertThat(roleIn(free, s.sectorA)).isEqualTo(TestAccess.OPERATOR);
    // Vincular outro gestor ao seu setor, ou como gestor, é papel do admin.
    assertThat(setRole(s.managerA, s.managerB, s.sectorA, TestAccess.CONSULT))
        .isEqualTo(HttpStatus.OK);
    assertThat(setRole(s.managerA, free, s.sectorA, TestAccess.MANAGER))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(roleIn(free, s.sectorA)).isEqualTo(TestAccess.OPERATOR);
    assertThat(setRole(s.admin, free, s.sectorA, TestAccess.MANAGER)).isEqualTo(HttpStatus.OK);
  }

  @Test
  void onlyAdminGrantsOrRevokesAdministrator() {
    Scenario s = new Scenario();
    String url = "/users/" + s.operatorA.getId() + "/administrator";
    Map<String, Object> grant = Map.of("administrator", true);

    for (User actor : List.of(s.managerA, s.operatorA, s.consultA)) {
      assertThat(status(actor, HttpMethod.PATCH, url, grant)).isEqualTo(HttpStatus.FORBIDDEN);
    }
    assertThat(users.findById(s.operatorA.getId()).orElseThrow().isAdministrator()).isFalse();
    assertThat(status(s.admin, HttpMethod.PATCH, url, grant)).isEqualTo(HttpStatus.OK);
    assertThat(users.findById(s.operatorA.getId()).orElseThrow().isAdministrator()).isTrue();
    // O novo administrador passa a ver todos os setores.
    assertThat(
            List.of(
                call(s.operatorA, HttpMethod.GET, "/sectors", null, SectorDto[].class).getBody()))
        .extracting(SectorDto::id)
        .contains(s.sectorA.id(), s.sectorB.id());
    assertThat(status(s.admin, HttpMethod.PATCH, url, Map.of("administrator", false)))
        .isEqualTo(HttpStatus.OK);
    assertThat(users.findById(s.operatorA.getId()).orElseThrow().isAdministrator()).isFalse();
    // Ninguém altera o próprio papel e o campo é obrigatório.
    assertThat(
            status(
                s.admin,
                HttpMethod.PATCH,
                "/users/" + s.admin.getId() + "/administrator",
                Map.of("administrator", false)))
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(status(s.admin, HttpMethod.PATCH, url, new HashMap<String, Object>()))
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(users.findById(s.admin.getId()).orElseThrow().isAdministrator()).isTrue();
  }

  // ---------------------------------------------------------------- desativar e excluir usuário

  @Test
  void adminDeactivatesUserWhoLosesAccessImmediatelyAndCanBeReactivated() {
    Scenario s = new Scenario();
    String url = "/users/" + s.operatorA.getId();

    var deactivated = call(s.admin, HttpMethod.PATCH, url + "/deactivate", null, UserDto.class);
    assertThat(deactivated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(deactivated.getBody().active()).isFalse();
    // A sessão que já estava aberta deixa de valer.
    assertThat(status(s.operatorA, HttpMethod.GET, "/inventory/user-inventories", null))
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    // O cadastro continua listado, marcado como inativo.
    var listed = List.of(call(s.admin, HttpMethod.GET, "/users", null, UserDto[].class).getBody());
    assertThat(listed)
        .filteredOn(user -> user.id() == s.operatorA.getId())
        .extracting(UserDto::active)
        .containsExactly(false);

    var reactivated = call(s.admin, HttpMethod.PATCH, url + "/activate", null, UserDto.class);
    assertThat(reactivated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(reactivated.getBody().active()).isTrue();
    assertThat(status(s.operatorA, HttpMethod.GET, "/inventory/user-inventories", null))
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void onlyAdminActivatesOrDeactivatesUsers() {
    Scenario s = new Scenario();
    User withoutAllocation = new User();
    withoutAllocation.setName("Sem alocação");
    withoutAllocation.setEmail("sem-alocacao-" + System.nanoTime() + "@ifpe.edu.br");
    withoutAllocation.setGoogleId(withoutAllocation.getEmail());
    withoutAllocation = users.save(withoutAllocation);
    String target = "/users/" + s.operatorA.getId();

    // Nem o gestor do próprio setor, nem operador, consulta ou quem ainda não tem alocação.
    for (User actor : List.of(s.managerA, s.operatorA, s.consultA, withoutAllocation)) {
      assertThat(status(actor, HttpMethod.PATCH, target + "/deactivate", null))
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(status(actor, HttpMethod.PATCH, target + "/activate", null))
          .isEqualTo(HttpStatus.FORBIDDEN);
    }
    assertThat(users.findById(s.operatorA.getId()).orElseThrow().isActive()).isTrue();

    // O administrador desativa qualquer um, inclusive o gestor e quem atua em vários setores.
    for (User other : List.of(s.operatorA, s.managerB)) {
      assertThat(status(s.admin, HttpMethod.PATCH, "/users/" + other.getId() + "/deactivate", null))
          .isEqualTo(HttpStatus.OK);
      assertThat(users.findById(other.getId()).orElseThrow().isActive()).isFalse();
    }
  }

  @Test
  void adminCannotDeactivateOrActivateOwnAccount() {
    Scenario s = new Scenario();

    assertThat(status(s.admin, HttpMethod.PATCH, "/users/" + s.admin.getId() + "/deactivate", null))
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(status(s.admin, HttpMethod.PATCH, "/users/" + s.admin.getId() + "/activate", null))
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(users.findById(s.admin.getId()).orElseThrow().isActive()).isTrue();
  }
}
