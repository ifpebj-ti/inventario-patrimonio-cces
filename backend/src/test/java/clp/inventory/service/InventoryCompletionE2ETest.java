package clp.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.dto.InventoryDto;
import clp.inventory.model.*;
import clp.inventory.repository.*;
import clp.inventory.testsupport.TestAccess;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.time.Instant;
import java.time.LocalDate;
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

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.mail.password=test"})
@AutoConfigureTestRestTemplate
class InventoryCompletionE2ETest {
  private static final String SECRET = "inventory-completion-test-secret-32-bytes";

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

  private User user(String roleName) {
    return access.user("completion-" + System.nanoTime() + "@ifpe.edu.br", roleName);
  }

  private HttpEntity<Void> request(User user) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(
        JWT.create()
            .withIssuer("inventory")
            .withSubject(user.getId().toString())
            .withExpiresAt(Instant.now().plusSeconds(3600))
            .sign(Algorithm.HMAC256(SECRET)));
    return new HttpEntity<>(headers);
  }

  private Inventory inventory(User owner, Sector sector) {
    return inventories.save(
        new Inventory(
            "Completion " + System.nanoTime(), "", LocalDate.now().plusDays(1), owner, sector));
  }

  private ResponseEntity<InventoryDto> editSector(Inventory inventory, User actor, Sector target) {
    var body =
        Map.of(
            "name",
            inventory.name(),
            "description",
            "",
            "plannedEndDate",
            LocalDate.now().plusDays(2).toString(),
            "sectorId",
            target.id());
    return http.exchange(
        "/inventory/" + inventory.id(),
        HttpMethod.PUT,
        new HttpEntity<>(body, request(actor).getHeaders()),
        InventoryDto.class);
  }

  @Test
  void adminCanEditSectorOfInventoryCreatedByAnotherUser() {
    User admin = user(TestAccess.ADMIN);
    Sector current = sectors.save(new Sector("Edit current " + System.nanoTime(), null));
    Sector target = sectors.save(new Sector("Edit target " + System.nanoTime(), null));
    Inventory inventory = inventory(user(TestAccess.OPERATOR), current);
    var response = editSector(inventory, admin, target);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().sectorId()).isEqualTo(target.id());
    assertThat(inventories.findById(inventory.id()).orElseThrow().sector().id())
        .isEqualTo(target.id());
  }

  @Test
  void managerCanMoveInventoryOnlyBetweenAllocatedSectors() {
    User manager = user(TestAccess.MANAGER);
    Sector current = sectors.save(new Sector("Manager edit current " + System.nanoTime(), null));
    Sector target = sectors.save(new Sector("Manager edit target " + System.nanoTime(), null));
    access.allocate(manager, current);
    access.allocate(manager, target);
    Inventory inventory = inventory(user(TestAccess.OPERATOR), current);
    assertThat(editSector(inventory, manager, target).getStatusCode()).isEqualTo(HttpStatus.OK);
    Sector forbidden = sectors.save(new Sector("Forbidden edit target " + System.nanoTime(), null));
    var body =
        Map.of(
            "name",
            inventory.name(),
            "plannedEndDate",
            LocalDate.now().plusDays(2).toString(),
            "sectorId",
            forbidden.id());
    var denied =
        http.exchange(
            "/inventory/" + inventory.id(),
            HttpMethod.PUT,
            new HttpEntity<>(body, request(manager).getHeaders()),
            String.class);
    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(inventories.findById(inventory.id()).orElseThrow().sector().id())
        .isEqualTo(target.id());
  }

  @Test
  void movingInventoryToInactiveSectorIsRejectedWithoutChangingCurrentSector() {
    User admin = user(TestAccess.ADMIN);
    Sector current = sectors.save(new Sector("Active edit source " + System.nanoTime(), null));
    Sector inactive = new Sector("Inactive edit target " + System.nanoTime(), null);
    inactive.setActive(false);
    sectors.save(inactive);
    Inventory inventory = inventory(admin, current);
    var body =
        Map.of(
            "name",
            inventory.name(),
            "plannedEndDate",
            LocalDate.now().plusDays(2).toString(),
            "sectorId",
            inactive.id());
    var response =
        http.exchange(
            "/inventory/" + inventory.id(),
            HttpMethod.PUT,
            new HttpEntity<>(body, request(admin).getHeaders()),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(inventories.findById(inventory.id()).orElseThrow().sector().id())
        .isEqualTo(current.id());
  }

  @Test
  void adminFinalizesLegacyInventoryAndRepeatedRequestPreservesPersistedDate() {
    User admin = user(TestAccess.ADMIN);
    Inventory inventory = inventory(user(TestAccess.OPERATOR), null);
    String path = "/inventory/" + inventory.id() + "/finalize";
    var response = http.exchange(path, HttpMethod.POST, request(admin), InventoryDto.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().status()).isEqualTo("CONCLUIDO");
    assertThat(response.getBody().completedAt()).isNotNull();
    var storedDate = inventories.findById(inventory.id()).orElseThrow().completedAt();
    var duplicate = http.exchange(path, HttpMethod.POST, request(admin), String.class);
    assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(inventories.findById(inventory.id()).orElseThrow().completedAt())
        .isEqualTo(storedDate);
  }

  @Test
  void sectorManagerListsAndFinalizesInventoryCreatedByAnotherUser() {
    User manager = user(TestAccess.MANAGER);
    Sector sector = sectors.save(new Sector("Completion sector " + System.nanoTime(), null));
    access.allocate(manager, sector);
    Inventory inventory = inventory(user(TestAccess.OPERATOR), sector);
    var list =
        http.exchange(
            "/inventory/user-inventories", HttpMethod.GET, request(manager), InventoryDto[].class);
    assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(list.getBody()).extracting(InventoryDto::id).containsExactly(inventory.id());
    var detail =
        http.exchange("/inventory/" + inventory.id(), HttpMethod.GET, request(manager), Map.class);
    assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(detail.getBody().get("canFinalize")).isEqualTo(true);
    var response =
        http.exchange(
            "/inventory/" + inventory.id() + "/finalize",
            HttpMethod.POST,
            request(manager),
            InventoryDto.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(inventories.findById(inventory.id()).orElseThrow().status())
        .isEqualTo(Inventory.Status.CONCLUIDO);
  }

  @Test
  void unrelatedManagerAndOwnerWithoutPermissionCannotFinalize() {
    User owner = user(TestAccess.OPERATOR);
    User manager = user(TestAccess.MANAGER);
    Sector sector = sectors.save(new Sector("Restricted sector " + System.nanoTime(), null));
    Inventory inventory = inventory(owner, sector);
    String path = "/inventory/" + inventory.id() + "/finalize";
    assertThat(http.exchange(path, HttpMethod.POST, request(manager), String.class).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(http.exchange(path, HttpMethod.POST, request(owner), String.class).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    var stored = inventories.findById(inventory.id()).orElseThrow();
    assertThat(stored.status()).isEqualTo(Inventory.Status.EM_ANDAMENTO);
    assertThat(stored.completedAt()).isNull();
  }
}
