package clp.inventory.user;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.model.User;
import clp.inventory.repository.SectorAllocationRepository;
import clp.inventory.repository.UserRepository;
import clp.inventory.testsupport.TestAccess;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.time.Duration;
import java.time.Instant;
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
 * Sobe a aplicação inteira (Postgres real via Testcontainers, HTTP real via TestRestTemplate). Como
 * User só nasce via /auth/google em produção, o usuário de teste é inserido direto via
 * UserRepository. Alocar/desalocar setor exige administrador (qualquer setor) ou gestor
 * (MANAGE_SECTOR) do setor informado, e a alocação sempre leva o cargo do usuário nesse setor. Um
 * usuário pode estar alocado a vários setores ao mesmo tempo, com cargos diferentes
 * (im_sector_allocation).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class UserSectorRoleE2ETest {

  private static final String TEST_SECRET = "test-secret-de-pelo-menos-32-bytes-aqui";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void authProperties(DynamicPropertyRegistry registry) {
    registry.add("security.token.secret", () -> TEST_SECRET);
  }

  @Autowired TestRestTemplate restTemplate;

  @Autowired UserRepository userRepository;

  @Autowired SectorAllocationRepository sectorAllocationRepository;

  @Autowired TestAccess access;

  private HttpHeaders authHeadersFor(long userId) {
    Algorithm algorithm = Algorithm.HMAC256(TEST_SECRET);
    String token =
        JWT.create()
            .withIssuer("inventory")
            .withSubject(String.valueOf(userId))
            .withExpiresAt(Instant.now().plus(Duration.ofHours(1)))
            .sign(algorithm);

    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(token);
    return headers;
  }

  // Usuário sem alocação e sem ser administrador: o estado de quem acabou de entrar pelo Google.
  private long createUser(String email) {
    User user = new User();
    user.setName("Test User");
    user.setEmail(email);
    user.setGoogleId("google-" + email);
    return userRepository.save(user).getId();
  }

  private long createUserWithRole(String email, String roleName) {
    return access.user(email, roleName).getId();
  }

  private void allocateSectorDirectly(long userId, long sectorId) {
    access.allocate(userId, sectorId);
  }

  private long adminUserId() {
    return access.admin("admin-" + System.nanoTime() + "@ifpe.edu.br").getId();
  }

  private long createSector(String name) {
    var body = new HashMap<String, Object>();
    body.put("name", name);
    var response =
        restTemplate.postForEntity(
            "/sectors", new HttpEntity<>(body, authHeadersFor(adminUserId())), Map.class);
    return ((Number) response.getBody().get("id")).longValue();
  }

  private long roleId(String name) {
    return access.role(name).id();
  }

  private ResponseEntity<Map> allocate(
      long userId, long sectorId, String roleName, long actingUserId) {
    return allocate(userId, sectorId, Map.of("roleId", roleId(roleName)), actingUserId);
  }

  private ResponseEntity<Map> allocate(
      long userId, long sectorId, Map<String, Object> body, long actingUserId) {
    return restTemplate.exchange(
        "/users/" + userId + "/sectors/" + sectorId,
        HttpMethod.PUT,
        new HttpEntity<>(body, authHeadersFor(actingUserId)),
        Map.class);
  }

  private ResponseEntity<Map> deallocate(long userId, long sectorId, long actingUserId) {
    return restTemplate.exchange(
        "/users/" + userId + "/sectors/" + sectorId,
        HttpMethod.DELETE,
        new HttpEntity<>(authHeadersFor(actingUserId)),
        Map.class);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> allocationsOf(ResponseEntity<Map> response) {
    return (List<Map<String, Object>>) response.getBody().get("allocations");
  }

  @Test
  void list_returnsAllUsers() {
    long adminId = adminUserId();
    createUser("list1@ifpe.edu.br");
    createUser("list2@ifpe.edu.br");

    var response =
        restTemplate.exchange(
            "/users", HttpMethod.GET, new HttpEntity<>(authHeadersFor(adminId)), List.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().size()).isGreaterThanOrEqualTo(2);
  }

  @Test
  void list_asUserWithoutAManagementRole_returns403() {
    long sectorId = createSector("Setor Listagem Sem Gestao");
    for (String roleName : List.of(TestAccess.CONSULT, TestAccess.OPERATOR)) {
      long userId = createUserWithRole(roleName + "-listagem@ifpe.edu.br", roleName);
      allocateSectorDirectly(userId, sectorId);

      var response =
          restTemplate.exchange(
              "/users", HttpMethod.GET, new HttpEntity<>(authHeadersFor(userId)), Map.class);

      assertThat(response.getStatusCode()).as(roleName).isEqualTo(HttpStatus.FORBIDDEN);
    }
    long unallocated = createUserWithRole("gestor-sem-alocacao@ifpe.edu.br", TestAccess.MANAGER);
    assertThat(
            restTemplate
                .exchange(
                    "/users",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeadersFor(unallocated)),
                    Map.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void list_asManager_returnsOnlyUsersOfTheSectorsHeManages() {
    long sectorA = createSector("Setor Gestor Lista A");
    long sectorB = createSector("Setor Gestor Lista B");
    long managerId = createUserWithRole("gestor-lista@ifpe.edu.br", TestAccess.MANAGER);
    allocateSectorDirectly(managerId, sectorA);
    long colleague = createUserWithRole("colega-lista@ifpe.edu.br", TestAccess.CONSULT);
    allocateSectorDirectly(colleague, sectorA);
    long stranger = createUserWithRole("estranho-lista@ifpe.edu.br", TestAccess.CONSULT);
    allocateSectorDirectly(stranger, sectorB);

    var ids = idsOf(getList("/users", managerId));

    assertThat(ids).contains(managerId, colleague).doesNotContain(stranger);
  }

  @Test
  void list_filterByRoleId_returnsOnlyUsersWithThatRoleInSomeSector() {
    long adminId = adminUserId();
    long sectorId = createSector("Setor Filtro Cargo");
    long operator = createUserWithRole("filtro-operador@ifpe.edu.br", TestAccess.OPERATOR);
    allocateSectorDirectly(operator, sectorId);
    long consult = createUserWithRole("filtro-consulta@ifpe.edu.br", TestAccess.CONSULT);
    allocateSectorDirectly(consult, sectorId);
    createUser("filtro-sem-cargo@ifpe.edu.br");

    var ids = idsOf(getList("/users?roleId=" + roleId(TestAccess.OPERATOR), adminId));

    assertThat(ids).contains(operator).doesNotContain(consult);
  }

  @Test
  void allocate_asAdmin_returnsUserWithTheRoleInTheSector() {
    long sectorId = createSector("Setor Atribuir");
    long adminId = adminUserId();
    long userId = createUser("atribuir-setor@ifpe.edu.br");

    var response = allocate(userId, sectorId, TestAccess.OPERATOR, adminId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((List<Integer>) response.getBody().get("sectorIds")).containsExactly((int) sectorId);
    assertThat(allocationsOf(response)).hasSize(1);
    assertThat(allocationsOf(response).get(0))
        .containsEntry("sectorId", (int) sectorId)
        .containsEntry("roleId", (int) roleId(TestAccess.OPERATOR))
        .containsEntry("roleName", TestAccess.OPERATOR);
  }

  @Test
  void allocate_userCanHaveDifferentRolesInDifferentSectors() {
    long sectorId1 = createSector("Setor Multiplo 1");
    long sectorId2 = createSector("Setor Multiplo 2");
    long adminId = adminUserId();
    long userId = createUser("multiplos-setores@ifpe.edu.br");

    allocate(userId, sectorId1, TestAccess.MANAGER, adminId);
    var response = allocate(userId, sectorId2, TestAccess.CONSULT, adminId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((List<Integer>) response.getBody().get("sectorIds"))
        .containsExactlyInAnyOrder((int) sectorId1, (int) sectorId2);
    assertThat(allocationsOf(response))
        .extracting(a -> a.get("roleName"))
        .containsExactlyInAnyOrder(TestAccess.MANAGER, TestAccess.CONSULT);
  }

  @Test
  void allocate_again_changesTheRoleWithoutDuplicatingTheAllocation() {
    long sectorId = createSector("Setor Troca De Cargo");
    long adminId = adminUserId();
    long userId = createUser("troca-cargo@ifpe.edu.br");

    allocate(userId, sectorId, TestAccess.CONSULT, adminId);
    var same = allocate(userId, sectorId, TestAccess.CONSULT, adminId);
    var changed = allocate(userId, sectorId, TestAccess.OPERATOR, adminId);

    assertThat(same.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(allocationsOf(same)).hasSize(1);
    assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(allocationsOf(changed)).hasSize(1);
    assertThat(allocationsOf(changed).get(0)).containsEntry("roleName", TestAccess.OPERATOR);
  }

  @Test
  void allocate_withoutRoleOrWithUnknownRole_returns400() {
    long sectorId = createSector("Setor Sem Cargo");
    long adminId = adminUserId();
    long userId = createUser("sem-cargo@ifpe.edu.br");

    assertThat(allocate(userId, sectorId, new HashMap<String, Object>(), adminId).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(allocate(userId, sectorId, Map.of("roleId", 999999), adminId).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(sectorAllocationRepository.existsByUser_IdAndSector_Id(userId, sectorId)).isFalse();
  }

  @Test
  void allocate_asGestorOfTargetSector_succeedsWithAnyRoleWithoutManagement() {
    long sectorId = createSector("Setor Gestor Mesmo Setor");
    long gestorId = createUserWithRole("gestor-mesmo-setor@ifpe.edu.br", TestAccess.MANAGER);
    allocateSectorDirectly(gestorId, sectorId);
    long userId = createUser("alocado-por-gestor@ifpe.edu.br");

    var response = allocate(userId, sectorId, TestAccess.OPERATOR, gestorId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((List<Integer>) response.getBody().get("sectorIds")).containsExactly((int) sectorId);
    // Dar cargo de gestão é do administrador.
    assertThat(allocate(userId, sectorId, TestAccess.MANAGER, gestorId).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void allocate_asGestorOfDifferentSector_returns403() {
    long sectorId = createSector("Setor Alvo Gestor Diferente");
    long outroSectorId = createSector("Outro Setor Gestor Diferente");
    long gestorId = createUserWithRole("gestor-outro-setor@ifpe.edu.br", TestAccess.MANAGER);
    allocateSectorDirectly(gestorId, outroSectorId);
    long userId = createUser("nao-alocado@ifpe.edu.br");

    var response = allocate(userId, sectorId, TestAccess.CONSULT, gestorId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void allocate_asUserWithoutPermission_returns403() {
    long sectorId = createSector("Setor Sem Permissao Atribuir");
    long consultaId = createUserWithRole("consulta-atribuir@ifpe.edu.br", TestAccess.CONSULT);
    allocateSectorDirectly(consultaId, sectorId);
    long userId = createUser("alvo-sem-permissao@ifpe.edu.br");

    var response = allocate(userId, sectorId, TestAccess.CONSULT, consultaId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void allocate_userNotFound_returns404() {
    long sectorId = createSector("Setor Usuario Inexistente");
    long adminId = adminUserId();

    var response = allocate(999999, sectorId, TestAccess.CONSULT, adminId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void allocate_sectorNotFound_returns404() {
    long adminId = adminUserId();
    long userId = createUser("setor-inexistente@ifpe.edu.br");

    var response = allocate(userId, 999999, TestAccess.CONSULT, adminId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void deallocate_asAdmin_removesSector() {
    long sectorId = createSector("Setor Desvincular");
    long adminId = adminUserId();
    long userId = createUser("desvincular-setor@ifpe.edu.br");
    allocate(userId, sectorId, TestAccess.CONSULT, adminId);

    var response = deallocate(userId, sectorId, adminId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((List<Integer>) response.getBody().get("sectorIds")).isEmpty();
  }

  @Test
  void deallocate_notAllocated_isIdempotent() {
    long sectorId = createSector("Setor Nunca Alocado");
    long adminId = adminUserId();
    long userId = createUser("nunca-alocado@ifpe.edu.br");

    var response = deallocate(userId, sectorId, adminId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((List<Integer>) response.getBody().get("sectorIds")).isEmpty();
  }

  @Test
  void deallocate_asGestorOfCurrentSector_succeeds() {
    long sectorId = createSector("Setor Desvincular Gestor Mesmo");
    long adminId = adminUserId();
    long userId = createUser("desvincular-por-gestor@ifpe.edu.br");
    allocate(userId, sectorId, TestAccess.CONSULT, adminId);
    long gestorId = createUserWithRole("gestor-desvincular@ifpe.edu.br", TestAccess.MANAGER);
    allocateSectorDirectly(gestorId, sectorId);

    var response = deallocate(userId, sectorId, gestorId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((List<Integer>) response.getBody().get("sectorIds")).isEmpty();
  }

  @Test
  void deallocate_asGestorOfDifferentSector_returns403() {
    long sectorId = createSector("Setor Desvincular Gestor Diferente");
    long outroSectorId = createSector("Outro Setor Desvincular");
    long adminId = adminUserId();
    long userId = createUser("desvincular-outro-gestor@ifpe.edu.br");
    allocate(userId, sectorId, TestAccess.CONSULT, adminId);
    long gestorId = createUserWithRole("gestor-outro-desvincular@ifpe.edu.br", TestAccess.MANAGER);
    allocateSectorDirectly(gestorId, outroSectorId);

    var response = deallocate(userId, sectorId, gestorId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void profileEndpointIsGone() {
    long adminId = adminUserId();
    long userId = createUser("sem-endpoint-perfil@ifpe.edu.br");

    var response =
        restTemplate.exchange(
            "/users/" + userId + "/profile",
            HttpMethod.PATCH,
            new HttpEntity<>(Map.of("profileId", 1), authHeadersFor(adminId)),
            Map.class);

    assertThat(response.getStatusCode().is2xxSuccessful()).isFalse();
  }

  @Test
  void unauthenticated_returns403() {
    var response = restTemplate.getForEntity("/users", Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  // --- Papel de administrador: só o administrador concede, e nunca a si mesmo ---

  private ResponseEntity<Map> patchAdministrator(long userId, Object value, long actingUserId) {
    var body = new HashMap<String, Object>();
    body.put("administrator", value);
    return restTemplate.exchange(
        "/users/" + userId + "/administrator",
        HttpMethod.PATCH,
        new HttpEntity<>(body, authHeadersFor(actingUserId)),
        Map.class);
  }

  @Test
  void administrator_adminGrantsAndRevokesForAnotherUser() {
    long actingAdminId = adminUserId();
    long userId = createUser("virar-admin@ifpe.edu.br");

    var granted = patchAdministrator(userId, true, actingAdminId);
    assertThat(granted.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(granted.getBody().get("administrator")).isEqualTo(true);
    assertThat(userRepository.findById(userId).orElseThrow().isAdministrator()).isTrue();

    var revoked = patchAdministrator(userId, false, actingAdminId);
    assertThat(revoked.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(userRepository.findById(userId).orElseThrow().isAdministrator()).isFalse();
  }

  @Test
  void administrator_nonAdminCannotGrantItNotEvenToThemselves() {
    long sectorId = createSector("Setor Auto Promocao");
    long managerId = createUserWithRole("auto-promocao@ifpe.edu.br", TestAccess.MANAGER);
    allocateSectorDirectly(managerId, sectorId);
    long targetId = createUser("alvo-promocao@ifpe.edu.br");

    assertThat(patchAdministrator(targetId, true, managerId).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(patchAdministrator(managerId, true, managerId).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(userRepository.findById(managerId).orElseThrow().isAdministrator()).isFalse();
    assertThat(userRepository.findById(targetId).orElseThrow().isAdministrator()).isFalse();
  }

  @Test
  void administrator_cannotChangeItsOwnRoleAndFieldIsRequired() {
    long adminId = adminUserId();
    long userId = createUser("campo-obrigatorio@ifpe.edu.br");

    assertThat(patchAdministrator(adminId, false, adminId).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(userRepository.findById(adminId).orElseThrow().isAdministrator()).isTrue();
    assertThat(patchAdministrator(userId, null, adminId).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(patchAdministrator(999999, true, adminId).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  // --- Conta de sistema: fora das listagens e protegida contra alteração pela API ---

  private User createSystemAccount() {
    User user = new User();
    user.setName("System Account");
    user.setEmail("sistema-" + System.nanoTime());
    user.setSystemUser(true);
    user.setAdministrator(true);
    return userRepository.save(user);
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> getList(String url, long actingUserId) {
    var response =
        restTemplate.exchange(
            url, HttpMethod.GET, new HttpEntity<>(authHeadersFor(actingUserId)), List.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return (List<Map<String, Object>>) response.getBody();
  }

  private static List<Long> idsOf(List<Map<String, Object>> users) {
    return users.stream().map(u -> ((Number) u.get("id")).longValue()).toList();
  }

  @Test
  void list_doesNotReturnTheSystemAccount() {
    long adminId = adminUserId();
    long regularId = createUser("lista-sem-sistema@ifpe.edu.br");
    User system = createSystemAccount();

    var ids = idsOf(getList("/users", adminId));

    assertThat(ids).contains(adminId, regularId).doesNotContain(system.getId());
  }

  @Test
  void sectorUsers_doesNotReturnTheSystemAccount() {
    long adminId = adminUserId();
    long sectorId = createSector("Setor Sem Conta De Sistema");
    long regularId = createUserWithRole("alocado-sem-sistema@ifpe.edu.br", TestAccess.CONSULT);
    User system = createSystemAccount();
    allocateSectorDirectly(regularId, sectorId);
    access.allocate(system, sectorRepositoryFind(sectorId), TestAccess.MANAGER);

    var ids = idsOf(getList("/sectors/" + sectorId + "/users", adminId));

    assertThat(ids).containsExactly(regularId);
  }

  @Autowired clp.inventory.repository.SectorRepository sectorRepository;

  private clp.inventory.model.Sector sectorRepositoryFind(long sectorId) {
    return sectorRepository.findById(sectorId).orElseThrow();
  }

  @Test
  void administrator_onTheSystemAccount_returns404AndKeepsIt() {
    User system = createSystemAccount();
    long adminId = adminUserId();

    assertThat(patchAdministrator(system.getId(), false, adminId).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(userRepository.findById(system.getId()).orElseThrow().isAdministrator()).isTrue();
  }

  @Test
  void allocate_theSystemAccount_returns404AndDoesNotAllocate() {
    long adminId = adminUserId();
    long sectorId = createSector("Setor Que Recusa Conta De Sistema");
    User system = createSystemAccount();

    var response = allocate(system.getId(), sectorId, TestAccess.CONSULT, adminId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(sectorAllocationRepository.existsByUser_IdAndSector_Id(system.getId(), sectorId))
        .isFalse();
  }

  @Test
  void deallocate_theSystemAccount_returns404() {
    long adminId = adminUserId();
    long sectorId = createSector("Setor Com Conta De Sistema Alocada");
    User system = createSystemAccount();
    access.allocate(system, sectorRepositoryFind(sectorId), TestAccess.MANAGER);

    var response = deallocate(system.getId(), sectorId, adminId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
