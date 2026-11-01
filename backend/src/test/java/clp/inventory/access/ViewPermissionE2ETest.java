package clp.inventory.access;

import static clp.inventory.service.AuthorizationService.MANAGE_ITEM;
import static clp.inventory.service.AuthorizationService.MANAGE_SECTOR;
import static clp.inventory.service.AuthorizationService.VIEW_SECTOR;
import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.dto.InventoryDto;
import clp.inventory.dto.SectorDto;
import clp.inventory.model.Inventory;
import clp.inventory.model.Item;
import clp.inventory.model.Role;
import clp.inventory.model.Sector;
import clp.inventory.model.User;
import clp.inventory.repository.InventoryRepository;
import clp.inventory.repository.ItemRepository;
import clp.inventory.repository.SectorRepository;
import clp.inventory.testsupport.TestAccess;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * A permissão de Consulta (VIEW_SECTOR) é a base da leitura: quem a tem lê tudo dos inventários do
 * setor em que está alocado (e só dele), sem alterar nada. Um cargo sem nenhuma permissão está
 * alocado mas não lê os dados do setor.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ViewPermissionE2ETest {

  private static final String SECRET = "view-permission-test-secret-32-bytes-long";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("security.token.secret", () -> SECRET);
  }

  @Autowired TestRestTemplate http;
  @Autowired TestAccess access;
  @Autowired SectorRepository sectors;
  @Autowired InventoryRepository inventories;
  @Autowired ItemRepository items;

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

  /** Dois setores, cada um com um inventário com um item. */
  private class Scenario {
    final Sector sectorA = sector("A");
    final Sector sectorB = sector("B");
    final User admin = access.admin("admin-" + System.nanoTime() + "@ifpe.edu.br");
    final Inventory inventoryA = inventory(sectorA);
    final Inventory inventoryB = inventory(sectorB);
    final Item itemA = firstItem(inventoryA);
    final Item itemB = firstItem(inventoryB);

    /** Usuário no setor A com um cargo de exatamente estas permissões (nenhuma = sem acesso). */
    User inA(String roleName, String... permissions) {
      Role role = access.customRole(roleName + " " + System.nanoTime(), permissions);
      User user = access.user("u-" + System.nanoTime() + "@ifpe.edu.br", TestAccess.CONSULT);
      access.allocate(user, sectorA, role.name());
      return user;
    }
  }

  private Sector sector(String label) {
    return sectors.save(new Sector("Consulta " + label + " " + System.nanoTime(), null));
  }

  private Inventory inventory(Sector sector) {
    User owner = access.user("owner-" + System.nanoTime() + "@ifpe.edu.br", TestAccess.CONSULT);
    Inventory inventory =
        new Inventory(
            "Consulta " + System.nanoTime(), "", LocalDate.now().plusDays(5), owner, sector);
    inventory.addItem(new Item("PAT" + System.nanoTime(), "", "Mesa", 1000, "Sala 1", "Maria"));
    return inventories.save(inventory);
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
    body.put("price", "R$ 10,00");
    body.put("locale", item.locale());
    body.put("isValid", valid);
    body.put("qrCode", item.qrCode());
    body.put("observations", List.of());
    return body;
  }

  private List<Long> inventoryIds(User user) {
    return List.of(
            call(user, HttpMethod.GET, "/inventory/user-inventories", null, InventoryDto[].class)
                .getBody())
        .stream()
        .map(InventoryDto::id)
        .toList();
  }

  @Test
  void viewPermissionReadsEverythingOfOwnSectorInventories() {
    Scenario s = new Scenario();
    User viewer = s.inA("Só consulta", VIEW_SECTOR);

    assertThat(inventoryIds(viewer)).contains(s.inventoryA.id()).doesNotContain(s.inventoryB.id());
    String details = "/inventory/" + s.inventoryA.id();
    String listing =
        "/inventory/inventory-items?inventoryId=" + s.inventoryA.id() + "&page=0&pageSize=10";
    String export = "/item/export-sheet?inventoryId=" + s.inventoryA.id();
    assertThat(status(viewer, HttpMethod.GET, details, null)).isEqualTo(HttpStatus.OK);
    assertThat(status(viewer, HttpMethod.GET, listing, null)).isEqualTo(HttpStatus.OK);
    assertThat(status(viewer, HttpMethod.GET, export, null)).isEqualTo(HttpStatus.OK);
    assertThat(status(viewer, HttpMethod.GET, "/item/" + s.itemA.id(), null))
        .isEqualTo(HttpStatus.OK);
    assertThat(status(viewer, HttpMethod.GET, "/sectors/" + s.sectorA.id(), null))
        .isEqualTo(HttpStatus.OK);
    var sectorList =
        List.of(call(viewer, HttpMethod.GET, "/sectors", null, SectorDto[].class).getBody());
    assertThat(sectorList).extracting(SectorDto::id).containsExactly(s.sectorA.id());
  }

  @Test
  void viewPermissionOnlyReadsItsOwnSector() {
    Scenario s = new Scenario();
    User viewer = s.inA("Só consulta", VIEW_SECTOR);

    String otherDetails = "/inventory/" + s.inventoryB.id();
    String otherListing =
        "/inventory/inventory-items?inventoryId=" + s.inventoryB.id() + "&page=0&pageSize=10";
    for (String url :
        List.of(
            otherDetails,
            otherListing,
            "/item/export-sheet?inventoryId=" + s.inventoryB.id(),
            "/item/" + s.itemB.id(),
            "/sectors/" + s.sectorB.id())) {
      assertThat(status(viewer, HttpMethod.GET, url, null)).as(url).isEqualTo(HttpStatus.FORBIDDEN);
    }
  }

  @Test
  void viewPermissionCannotChangeAnything() {
    Scenario s = new Scenario();
    User viewer = s.inA("Só consulta", VIEW_SECTOR);
    String inventoryUrl = "/inventory/" + s.inventoryA.id();
    Map<String, Object> inventoryEdit = new HashMap<>();
    inventoryEdit.put("name", "Alterado");
    inventoryEdit.put("description", "");
    inventoryEdit.put("plannedEndDate", LocalDate.now().plusDays(9).toString());
    inventoryEdit.put("sectorId", s.sectorA.id());

    // Validar/editar item, observar, editar/finalizar/excluir inventário e criar inventário.
    assertThat(
            status(
                viewer, HttpMethod.PUT, "/item/" + s.itemA.id(), itemBody(s.itemA, true, "Mesa")))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            status(
                viewer,
                HttpMethod.PATCH,
                "/item/" + s.itemA.id() + "/notes",
                List.of(Map.of("content", "Observação"))))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(status(viewer, HttpMethod.PUT, inventoryUrl, inventoryEdit))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(status(viewer, HttpMethod.POST, inventoryUrl + "/finalize", null))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(status(viewer, HttpMethod.DELETE, inventoryUrl, null))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            status(
                viewer,
                HttpMethod.DELETE,
                "/inventory/clear-items?inventoryId=" + s.inventoryA.id(),
                null))
        .isEqualTo(HttpStatus.FORBIDDEN);
    inventoryEdit.put("name", "Novo " + System.nanoTime());
    assertThat(status(viewer, HttpMethod.POST, "/inventory/new-inventory", inventoryEdit))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(inventories.findById(s.inventoryA.id())).isPresent();
    assertThat(items.findById(s.itemA.id()).orElseThrow().isValid()).isFalse();
  }

  @Test
  void roleWithoutAnyPermissionIsAllocatedButReadsNothing() {
    Scenario s = new Scenario();
    User blind = s.inA("Sem acesso");

    // Alocado, mas sem ler: nada nas listas e 403 em tudo que é dado do setor.
    assertThat(inventoryIds(blind)).doesNotContain(s.inventoryA.id());
    assertThat(List.of(call(blind, HttpMethod.GET, "/sectors", null, SectorDto[].class).getBody()))
        .extracting(SectorDto::id)
        .doesNotContain(s.sectorA.id());
    for (String url :
        List.of(
            "/inventory/" + s.inventoryA.id(),
            "/inventory/inventory-items?inventoryId=" + s.inventoryA.id() + "&page=0&pageSize=10",
            "/item/export-sheet?inventoryId=" + s.inventoryA.id(),
            "/item/" + s.itemA.id(),
            "/sectors/" + s.sectorA.id())) {
      assertThat(status(blind, HttpMethod.GET, url, null)).as(url).isEqualTo(HttpStatus.FORBIDDEN);
    }
  }

  @Test
  void managementPermissionsAlsoRead() {
    Scenario s = new Scenario();
    // Mesmo sem a Consulta marcada (cargo antigo ou criado direto no banco), gerenciar inclui ler.
    User operator = s.inA("Só operar", MANAGE_ITEM);
    User manager = s.inA("Só gerir", MANAGE_SECTOR);

    for (User user : List.of(operator, manager)) {
      assertThat(inventoryIds(user)).contains(s.inventoryA.id()).doesNotContain(s.inventoryB.id());
      assertThat(status(user, HttpMethod.GET, "/inventory/" + s.inventoryA.id(), null))
          .isEqualTo(HttpStatus.OK);
      assertThat(status(user, HttpMethod.GET, "/item/" + s.itemA.id(), null))
          .isEqualTo(HttpStatus.OK);
    }
  }

  @Test
  void changingTheRoleChangesWhatTheUserReads() {
    Scenario s = new Scenario();
    User user = s.inA("Só consulta", VIEW_SECTOR);
    assertThat(inventoryIds(user)).contains(s.inventoryA.id());

    Role empty = access.customRole("Cargo vazio " + System.nanoTime());
    access.allocate(user, s.sectorA, empty.name());

    assertThat(inventoryIds(user)).doesNotContain(s.inventoryA.id());
  }
}
