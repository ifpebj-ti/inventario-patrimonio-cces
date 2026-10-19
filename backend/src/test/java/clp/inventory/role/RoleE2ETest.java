package clp.inventory.role;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.dto.PermissionDto;
import clp.inventory.dto.RoleDto;
import clp.inventory.model.Sector;
import clp.inventory.model.User;
import clp.inventory.repository.SectorRepository;
import clp.inventory.testsupport.TestAccess;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/** CRUD de cargos: só o administrador altera; o gestor lê; a exclusão tem regras de proteção. */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.mail.password=test")
@AutoConfigureTestRestTemplate
class RoleE2ETest {
  private static final String SECRET = "role-crud-test-secret-de-pelo-menos-32-bytes";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("security.token.secret", () -> SECRET);
  }

  @Autowired TestRestTemplate http;
  @Autowired TestAccess access;
  @Autowired SectorRepository sectors;

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

  private User admin() {
    return access.admin("admin-" + System.nanoTime() + "@ifpe.edu.br");
  }

  private User user(String roleName) {
    return access.user("u-" + System.nanoTime() + "@ifpe.edu.br", roleName);
  }

  private Sector sector() {
    return sectors.save(new Sector("Setor cargo " + System.nanoTime(), null));
  }

  private long permissionId(User admin, String name) {
    return List.of(
            call(admin, HttpMethod.GET, "/permissions", null, PermissionDto[].class).getBody())
        .stream()
        .filter(p -> p.name().equals(name))
        .findFirst()
        .orElseThrow()
        .id();
  }

  private Map<String, Object> body(String name, String description, Set<Long> permissionIds) {
    Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("description", description);
    body.put("permissionIds", permissionIds);
    return body;
  }

  private RoleDto create(User admin, String name, Set<Long> permissionIds) {
    var response =
        call(admin, HttpMethod.POST, "/roles", body(name, "Teste", permissionIds), RoleDto.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response.getBody();
  }

  private List<RoleDto> list(User user) {
    return List.of(call(user, HttpMethod.GET, "/roles", null, RoleDto[].class).getBody());
  }

  @Test
  void adminListsTheSeededRolesAndNoAdministratorRole() {
    User admin = admin();
    var roles = list(admin);

    assertThat(roles)
        .extracting(RoleDto::name)
        .contains(TestAccess.MANAGER, TestAccess.OPERATOR, TestAccess.CONSULT)
        .doesNotContain("ADMIN_ORGANIZATION");
    assertThat(roles)
        .filteredOn(r -> r.name().equals(TestAccess.CONSULT))
        .allSatisfy(
            r -> {
              assertThat(r.permissionIds()).isEmpty();
              assertThat(r.management()).isFalse();
            });
    assertThat(roles)
        .filteredOn(r -> r.name().equals(TestAccess.MANAGER))
        .allSatisfy(
            r -> {
              assertThat(r.permissionIds()).containsExactly(permissionId(admin, "MANAGE_SECTOR"));
              assertThat(r.management()).isTrue();
              assertThat(r.deletable()).isFalse();
            });
    var permissionNames =
        List.of(call(admin, HttpMethod.GET, "/permissions", null, PermissionDto[].class).getBody())
            .stream()
            .map(PermissionDto::name)
            .toList();
    assertThat(permissionNames).containsExactlyInAnyOrder("MANAGE_SECTOR", "MANAGE_ITEM");
  }

  @Test
  void adminCreatesUpdatesAndValidatesRoles() {
    User admin = admin();
    long manageItem = permissionId(admin, "MANAGE_ITEM");

    RoleDto created = create(admin, "Auditor " + System.nanoTime(), Set.of(manageItem));
    assertThat(created.permissionIds()).containsExactly(manageItem);
    assertThat(created.management()).isFalse();
    assertThat(created.allocationCount()).isZero();

    String url = "/roles/" + created.id();
    var updated =
        call(
            admin,
            HttpMethod.PUT,
            url,
            body("Auditor renomeado " + created.id(), "Nova descrição", Set.of()),
            RoleDto.class);
    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(updated.getBody().name()).isEqualTo("Auditor renomeado " + created.id());
    assertThat(updated.getBody().permissionIds()).isEmpty();
    assertThat(updated.getBody().deletable()).isTrue();

    // Validações: nome vazio, repetido, grande demais e permissão inexistente.
    assertThat(status(admin, HttpMethod.POST, "/roles", body(" ", null, Set.of())))
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(status(admin, HttpMethod.POST, "/roles", body(TestAccess.CONSULT, null, Set.of())))
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(status(admin, HttpMethod.POST, "/roles", body("x".repeat(101), null, Set.of())))
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            status(
                admin,
                HttpMethod.POST,
                "/roles",
                body("Com permissão fantasma " + System.nanoTime(), null, Set.of(999999L))))
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(status(admin, HttpMethod.PUT, url, body(TestAccess.MANAGER, null, Set.of())))
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(status(admin, HttpMethod.PUT, "/roles/999999", body("Qualquer", null, Set.of())))
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void onlyAdminCreatesEditsOrDeletesRoles() {
    User admin = admin();
    RoleDto role = create(admin, "Protegido " + System.nanoTime(), Set.of());
    Sector sector = sector();
    User manager = user(TestAccess.MANAGER);
    access.allocate(manager, sector);

    for (User actor : List.of(manager, user(TestAccess.OPERATOR), user(TestAccess.CONSULT))) {
      assertThat(status(actor, HttpMethod.POST, "/roles", body("Novo", null, Set.of())))
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(
              status(actor, HttpMethod.PUT, "/roles/" + role.id(), body("Outro", null, Set.of())))
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(status(actor, HttpMethod.DELETE, "/roles/" + role.id(), null))
          .isEqualTo(HttpStatus.FORBIDDEN);
    }
    assertThat(list(admin)).extracting(RoleDto::id).contains(role.id());
  }

  @Test
  void managerReadsRolesButOperatorConsultAndUnallocatedUsersDoNot() {
    Sector sector = sector();
    User manager = user(TestAccess.MANAGER);
    access.allocate(manager, sector);

    assertThat(list(manager))
        .extracting(RoleDto::name)
        .contains(TestAccess.MANAGER, TestAccess.OPERATOR, TestAccess.CONSULT);
    for (String roleName : List.of(TestAccess.OPERATOR, TestAccess.CONSULT)) {
      User other = user(roleName);
      access.allocate(other, sector);
      assertThat(status(other, HttpMethod.GET, "/roles", null))
          .as(roleName)
          .isEqualTo(HttpStatus.FORBIDDEN);
    }
    assertThat(status(user(TestAccess.CONSULT), HttpMethod.GET, "/roles", null))
        .isEqualTo(HttpStatus.FORBIDDEN);
    // Por id, só o administrador.
    assertThat(status(manager, HttpMethod.GET, "/roles/" + list(manager).get(0).id(), null))
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void roleWithAllocationsOrPermissionsCannotBeDeleted() {
    User admin = admin();
    long manageItem = permissionId(admin, "MANAGE_ITEM");

    // Em uso por uma alocação: 409, mesmo sem permissão alguma.
    RoleDto inUse = create(admin, "Em uso " + System.nanoTime(), Set.of());
    access.allocate(user(TestAccess.CONSULT), sector(), inUse.name());
    assertThat(status(admin, HttpMethod.DELETE, "/roles/" + inUse.id(), null))
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(list(admin))
        .filteredOn(r -> r.id() == inUse.id())
        .allSatisfy(
            r -> {
              assertThat(r.deletable()).isFalse();
              assertThat(r.allocationCount()).isEqualTo(1);
            });

    // Com permissão marcada, sem alocação: 409 até retirar as permissões.
    RoleDto withPermission =
        create(admin, "Com permissão " + System.nanoTime(), Set.of(manageItem));
    assertThat(status(admin, HttpMethod.DELETE, "/roles/" + withPermission.id(), null))
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            status(
                admin,
                HttpMethod.PUT,
                "/roles/" + withPermission.id(),
                body(withPermission.name(), "Sem permissões", Set.of())))
        .isEqualTo(HttpStatus.OK);

    // Sem alocação e sem permissão: exclui.
    assertThat(status(admin, HttpMethod.DELETE, "/roles/" + withPermission.id(), null))
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(list(admin)).extracting(RoleDto::id).doesNotContain(withPermission.id());
    assertThat(status(admin, HttpMethod.DELETE, "/roles/" + withPermission.id(), null))
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void countsFollowAllocationsAndUsers() {
    User admin = admin();
    RoleDto role = create(admin, "Contado " + System.nanoTime(), Set.of());
    Sector a = sector();
    Sector b = sector();
    User first = user(TestAccess.CONSULT);
    User second = user(TestAccess.CONSULT);
    access.allocate(first, a, role.name());
    access.allocate(first, b, role.name());
    access.allocate(second, a, role.name());

    var counted = list(admin).stream().filter(r -> r.id() == role.id()).findFirst().orElseThrow();
    assertThat(counted.allocationCount()).isEqualTo(3);
    assertThat(counted.userCount()).isEqualTo(2);
  }

  @Test
  void authorizationFollowsThePermissionsOfTheRoleNotItsName() {
    User admin = admin();
    long manageSector = permissionId(admin, "MANAGE_SECTOR");
    RoleDto coordinator = create(admin, "Coordenador " + System.nanoTime(), Set.of(manageSector));
    assertThat(coordinator.management()).isTrue();
    Sector sector = sector();
    User user = user(TestAccess.CONSULT);
    access.allocate(user, sector, coordinator.name());

    Map<String, Object> inventory = new HashMap<>();
    inventory.put("name", "Inventário do coordenador " + System.nanoTime());
    inventory.put("description", "");
    inventory.put("plannedEndDate", LocalDate.now().plusDays(10).toString());
    inventory.put("sectorId", sector.id());
    assertThat(status(user, HttpMethod.POST, "/inventory/new-inventory", inventory))
        .isEqualTo(HttpStatus.CREATED);

    // Tirar a permissão do cargo tira o poder na hora, para todas as alocações dele.
    assertThat(
            status(
                admin,
                HttpMethod.PUT,
                "/roles/" + coordinator.id(),
                body(coordinator.name(), null, Set.of())))
        .isEqualTo(HttpStatus.OK);
    inventory.put("name", "Inventário depois " + System.nanoTime());
    assertThat(status(user, HttpMethod.POST, "/inventory/new-inventory", inventory))
        .isEqualTo(HttpStatus.FORBIDDEN);
  }
}
