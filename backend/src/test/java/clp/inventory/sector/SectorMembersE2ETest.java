package clp.inventory.sector;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.model.Sector;
import clp.inventory.model.User;
import clp.inventory.repository.SectorAllocationRepository;
import clp.inventory.repository.SectorRepository;
import clp.inventory.repository.UserRepository;
import clp.inventory.testsupport.TestAccess;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
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
 * Alocar usuários a partir do setor: a lista de quem pode ser alocado e a alocação em lote com um
 * cargo só. Administrador age em qualquer setor; o gestor, só nos setores que gerencia e só com
 * cargos sem permissão de gestão.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class SectorMembersE2ETest {

  private static final String SECRET = "sector-members-test-secret-de-pelo-menos-32-bytes";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("security.token.secret", () -> SECRET);
  }

  @Autowired TestRestTemplate http;
  @Autowired TestAccess access;
  @Autowired SectorRepository sectors;
  @Autowired UserRepository users;
  @Autowired SectorAllocationRepository allocations;

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

  private User admin() {
    return access.admin("admin-" + System.nanoTime() + "@ifpe.edu.br");
  }

  private User user(String roleName) {
    return access.user("u-" + System.nanoTime() + "@ifpe.edu.br", roleName);
  }

  private Sector sector() {
    return sectors.save(new Sector("Setor membros " + System.nanoTime(), null));
  }

  private User managerOf(Sector sector) {
    User manager = user(TestAccess.MANAGER);
    access.allocate(manager, sector);
    return manager;
  }

  private String candidatesUrl(Sector sector) {
    return "/sectors/" + sector.id() + "/assignable-users";
  }

  private String membersUrl(Sector sector) {
    return "/sectors/" + sector.id() + "/members";
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> candidates(User actor, Sector sector) {
    var response = call(actor, HttpMethod.GET, candidatesUrl(sector), null, List.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return (List<Map<String, Object>>) response.getBody();
  }

  private List<Long> ids(List<Map<String, Object>> rows) {
    return rows.stream().map(row -> ((Number) row.get("id")).longValue()).toList();
  }

  private Map<String, Object> batch(List<User> targets, String roleName) {
    Map<String, Object> body = new HashMap<>();
    body.put("userIds", targets.stream().map(User::getId).toList());
    body.put("roleId", access.role(roleName).id());
    return body;
  }

  private String roleIn(User user, Sector sector) {
    return allocations
        .findByUser_IdAndSector_Id(user.getId(), sector.id())
        .orElseThrow()
        .role()
        .name();
  }

  // --------------------------------------------------------------- candidatos

  @Test
  void candidates_listsActiveUsersNotYetInTheSectorWithOnlyNameAndEmail() {
    Sector sector = sector();
    User admin = admin();
    User free = user(TestAccess.CONSULT);
    User alreadyIn = user(TestAccess.CONSULT);
    access.allocate(alreadyIn, sector);
    User inactive = user(TestAccess.CONSULT);
    inactive.setActive(false);
    users.save(inactive);
    User system = new User();
    system.setName("Sistema");
    system.setEmail("sistema-" + System.nanoTime());
    system.setSystemUser(true);
    system.setAdministrator(true);
    system = users.save(system);

    var rows = candidates(admin, sector);

    assertThat(ids(rows)).contains(free.getId()).doesNotContain(alreadyIn.getId());
    assertThat(ids(rows)).doesNotContain(inactive.getId(), system.getId());
    // Só o necessário para escolher: nada de cargos, setores ou papel de administrador.
    assertThat(rows)
        .allSatisfy(
            row -> assertThat(row.keySet()).containsExactlyInAnyOrder("id", "name", "email"));
  }

  @Test
  void candidates_managerOfTheSectorSeesUsersWhoHaveNoSectorYet() {
    Sector sector = sector();
    User manager = managerOf(sector);
    User newcomer = new User();
    newcomer.setName("Recém-chegado");
    newcomer.setEmail("novo-" + System.nanoTime() + "@ifpe.edu.br");
    newcomer.setGoogleId("google-" + newcomer.getEmail());
    newcomer = users.save(newcomer);

    assertThat(ids(candidates(manager, sector))).contains(newcomer.getId());
  }

  @Test
  void candidates_onlyAdminAndManagerOfThatSector() {
    Sector sector = sector();
    Sector other = sector();
    User managerOfOther = managerOf(other);
    User operator = user(TestAccess.OPERATOR);
    access.allocate(operator, sector);
    User consult = user(TestAccess.CONSULT);
    access.allocate(consult, sector);

    for (User actor : List.of(managerOfOther, operator, consult, user(TestAccess.CONSULT))) {
      assertThat(
              call(actor, HttpMethod.GET, candidatesUrl(sector), null, String.class)
                  .getStatusCode())
          .isEqualTo(HttpStatus.FORBIDDEN);
    }
    assertThat(
            call(admin(), HttpMethod.GET, "/sectors/999999/assignable-users", null, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  // --------------------------------------------------------------- alocação em lote

  @Test
  void allocateMany_adminAllocatesSeveralUsersWithOneRoleInAnySector() {
    Sector sector = sector();
    User admin = admin();
    User first = user(TestAccess.CONSULT);
    User second = user(TestAccess.CONSULT);

    var response =
        call(
            admin,
            HttpMethod.PUT,
            membersUrl(sector),
            batch(List.of(first, second), TestAccess.MANAGER),
            List.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(roleIn(first, sector)).isEqualTo(TestAccess.MANAGER);
    assertThat(roleIn(second, sector)).isEqualTo(TestAccess.MANAGER);
    // Devolve os membros do setor.
    @SuppressWarnings("unchecked")
    var members = (List<Map<String, Object>>) response.getBody();
    assertThat(ids(members)).containsExactlyInAnyOrder(first.getId(), second.getId());
    // Quem foi alocado deixa de ser candidato.
    assertThat(ids(candidates(admin, sector))).doesNotContain(first.getId(), second.getId());
  }

  @Test
  void allocateMany_managerAllocatesWithRolesWithoutManagementInOwnSector() {
    Sector sector = sector();
    User manager = managerOf(sector);
    User first = user(TestAccess.CONSULT);
    User second = user(TestAccess.CONSULT);

    var response =
        call(
            manager,
            HttpMethod.PUT,
            membersUrl(sector),
            batch(List.of(first, second), TestAccess.OPERATOR),
            List.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(roleIn(first, sector)).isEqualTo(TestAccess.OPERATOR);
    assertThat(roleIn(second, sector)).isEqualTo(TestAccess.OPERATOR);
  }

  @Test
  void allocateMany_managerCannotGiveAManagementRoleAndNobodyIsAllocated() {
    Sector sector = sector();
    User manager = managerOf(sector);
    User first = user(TestAccess.CONSULT);
    User second = user(TestAccess.CONSULT);

    var response =
        call(
            manager,
            HttpMethod.PUT,
            membersUrl(sector),
            batch(List.of(first, second), TestAccess.MANAGER),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(allocations.existsByUser_IdAndSector_Id(first.getId(), sector.id())).isFalse();
    assertThat(allocations.existsByUser_IdAndSector_Id(second.getId(), sector.id())).isFalse();
  }

  @Test
  void allocateMany_managerCannotTouchAnotherManagerOrAnAdministratorAndTheBatchIsAtomic() {
    Sector sector = sector();
    User manager = managerOf(sector);
    User otherManager = managerOf(sector);
    User administrator = admin();
    User plain = user(TestAccess.CONSULT);

    // Outro gestor do setor no meio do lote: nada é alocado, nem o usuário comum.
    var withManager =
        call(
            manager,
            HttpMethod.PUT,
            membersUrl(sector),
            batch(List.of(plain, otherManager), TestAccess.CONSULT),
            String.class);
    var withAdministrator =
        call(
            manager,
            HttpMethod.PUT,
            membersUrl(sector),
            batch(List.of(plain, administrator), TestAccess.CONSULT),
            String.class);

    assertThat(withManager.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(withAdministrator.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(allocations.existsByUser_IdAndSector_Id(plain.getId(), sector.id())).isFalse();
    assertThat(roleIn(otherManager, sector)).isEqualTo(TestAccess.MANAGER);
  }

  @Test
  void allocateMany_onlyAdminOrManagerOfThatSector() {
    Sector sector = sector();
    Sector other = sector();
    User managerOfOther = managerOf(other);
    User operator = user(TestAccess.OPERATOR);
    access.allocate(operator, sector);
    User target = user(TestAccess.CONSULT);

    for (User actor : List.of(managerOfOther, operator, user(TestAccess.CONSULT))) {
      assertThat(
              call(
                      actor,
                      HttpMethod.PUT,
                      membersUrl(sector),
                      batch(List.of(target), TestAccess.CONSULT),
                      String.class)
                  .getStatusCode())
          .isEqualTo(HttpStatus.FORBIDDEN);
    }
    assertThat(allocations.existsByUser_IdAndSector_Id(target.getId(), sector.id())).isFalse();
  }

  @Test
  void allocateMany_unknownUserOrSystemAccountIs404AndTheBatchIsAtomic() {
    Sector sector = sector();
    User admin = admin();
    User plain = user(TestAccess.CONSULT);
    User system = new User();
    system.setName("Sistema");
    system.setEmail("sistema-lote-" + System.nanoTime());
    system.setSystemUser(true);
    system.setAdministrator(true);
    system = users.save(system);

    Map<String, Object> unknown = batch(List.of(plain), TestAccess.CONSULT);
    unknown.put("userIds", Arrays.asList(plain.getId(), 999999L));
    assertThat(
            call(admin, HttpMethod.PUT, membersUrl(sector), unknown, String.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            call(
                    admin,
                    HttpMethod.PUT,
                    membersUrl(sector),
                    batch(List.of(plain, system), TestAccess.CONSULT),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(allocations.existsByUser_IdAndSector_Id(plain.getId(), sector.id())).isFalse();
    assertThat(
            call(
                    admin,
                    HttpMethod.PUT,
                    "/sectors/999999/members",
                    batch(List.of(plain), TestAccess.CONSULT),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void allocateMany_alreadyAllocatedUserHasTheRoleChanged() {
    Sector sector = sector();
    User admin = admin();
    User member = user(TestAccess.CONSULT);
    access.allocate(member, sector, TestAccess.CONSULT);

    var response =
        call(
            admin,
            HttpMethod.PUT,
            membersUrl(sector),
            batch(List.of(member), TestAccess.OPERATOR),
            List.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(roleIn(member, sector)).isEqualTo(TestAccess.OPERATOR);
    assertThat(allocations.findBySector_Id(sector.id())).hasSize(1);
  }

  @Test
  void allocateMany_validatesTheBody() {
    Sector sector = sector();
    User admin = admin();
    User target = user(TestAccess.CONSULT);
    Map<String, Object> noRole = batch(List.of(target), TestAccess.CONSULT);
    noRole.remove("roleId");
    Map<String, Object> unknownRole = batch(List.of(target), TestAccess.CONSULT);
    unknownRole.put("roleId", 999999);
    Map<String, Object> empty = batch(List.of(target), TestAccess.CONSULT);
    empty.put("userIds", List.of());
    Map<String, Object> repeated = batch(List.of(target), TestAccess.CONSULT);
    repeated.put("userIds", List.of(target.getId(), target.getId()));
    Map<String, Object> tooMany = batch(List.of(target), TestAccess.CONSULT);
    List<Long> many = new ArrayList<>();
    for (long i = 1; i <= 201; i++) {
      many.add(i);
    }
    tooMany.put("userIds", many);

    for (Map<String, Object> body : List.of(noRole, unknownRole, empty, repeated, tooMany)) {
      assertThat(
              call(admin, HttpMethod.PUT, membersUrl(sector), body, String.class).getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
    assertThat(allocations.existsByUser_IdAndSector_Id(target.getId(), sector.id())).isFalse();
  }
}
