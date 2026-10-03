package clp.inventory.user;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.model.Sector;
import clp.inventory.model.SectorAllocation;
import clp.inventory.model.User;
import clp.inventory.repository.ProfileRepository;
import clp.inventory.repository.SectorAllocationRepository;
import clp.inventory.repository.SectorRepository;
import clp.inventory.repository.UserRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
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
 * UserRepository — mais simples que subir o servidor JWKS fake só para ter uma linha em im_user.
 * Alocar/desalocar setor exige ADMIN (qualquer setor) ou MANAGE_SECTOR + estar alocado no setor
 * informado, então os testes de alocação precisam de um usuário real com um perfil seedado. Um
 * usuário pode estar alocado a vários setores ao mesmo tempo (im_sector_allocation).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserSectorProfileE2ETest {

  private static final String TEST_SECRET = "test-secret-de-pelo-menos-32-bytes-aqui";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void authProperties(DynamicPropertyRegistry registry) {
    registry.add("security.token.secret", () -> TEST_SECRET);
  }

  @Autowired TestRestTemplate restTemplate;

  @Autowired UserRepository userRepository;

  @Autowired ProfileRepository profileRepository;

  @Autowired SectorRepository sectorRepository;

  @Autowired SectorAllocationRepository sectorAllocationRepository;

  private HttpHeaders authHeaders() {
    return authHeadersFor(1);
  }

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

  private long createUser(String email) {
    User user = new User();
    user.setName("Test User");
    user.setEmail(email);
    user.setGoogleId("google-" + email);
    return userRepository.save(user).getId();
  }

  private long createUserWithProfile(String email, String profileName) {
    var profile =
        profileRepository.findAll().stream()
            .filter(p -> profileName.equals(p.name()))
            .findFirst()
            .orElseThrow();
    User user = new User();
    user.setName("Test User");
    user.setEmail(email);
    user.setGoogleId("google-" + email);
    user.setProfile(profile);
    return userRepository.save(user).getId();
  }

  private void allocateSectorDirectly(long userId, long sectorId) {
    User user = userRepository.findById(userId).orElseThrow();
    Sector sector = sectorRepository.findById(sectorId).orElseThrow();
    sectorAllocationRepository.save(new SectorAllocation(user, sector));
  }

  private long adminUserId() {
    return createUserWithProfile(
        "admin-" + System.nanoTime() + "@ifpe.edu.br", "ADMIN_ORGANIZATION");
  }

  private long createSector(String name) {
    var body = new HashMap<String, Object>();
    body.put("name", name);
    var response =
        restTemplate.postForEntity(
            "/sectors", new HttpEntity<>(body, authHeadersFor(adminUserId())), Map.class);
    return ((Number) response.getBody().get("id")).longValue();
  }

  private long createProfile(String name) {
    var body = new HashMap<String, Object>();
    body.put("name", name);
    var response =
        restTemplate.postForEntity("/profiles", new HttpEntity<>(body, authHeaders()), Map.class);
    return ((Number) response.getBody().get("id")).longValue();
  }

  private ResponseEntity<Map> allocate(long userId, long sectorId, long actingUserId) {
    return restTemplate.exchange(
        "/users/" + userId + "/sectors/" + sectorId,
        HttpMethod.POST,
        new HttpEntity<>(authHeadersFor(actingUserId)),
        Map.class);
  }

  private ResponseEntity<Map> deallocate(long userId, long sectorId, long actingUserId) {
    return restTemplate.exchange(
        "/users/" + userId + "/sectors/" + sectorId,
        HttpMethod.DELETE,
        new HttpEntity<>(authHeadersFor(actingUserId)),
        Map.class);
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
  void list_asNonAdmin_returns403() {
    long consultaId = createUserWithProfile("consulta-listagem-usuarios@ifpe.edu.br", "CONSULTA");

    var response =
        restTemplate.exchange(
            "/users", HttpMethod.GET, new HttpEntity<>(authHeadersFor(consultaId)), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void list_filterByProfileId_returnsOnlyUsersWithThatProfile() {
    long adminId = adminUserId();
    long profileId = createProfile("Perfil Filtro Usuario");
    long userId = createUser("filtro-perfil@ifpe.edu.br");
    createUser("sem-perfil@ifpe.edu.br");

    restTemplate.exchange(
        "/users/" + userId + "/profile",
        HttpMethod.PATCH,
        new HttpEntity<>(Map.of("profileId", profileId), authHeaders()),
        Map.class);

    var response =
        restTemplate.exchange(
            "/users?profileId=" + profileId,
            HttpMethod.GET,
            new HttpEntity<>(authHeadersFor(adminId)),
            List.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).hasSize(1);
  }

  @Test
  void allocate_asAdmin_returnsUserWithSectorId() {
    long sectorId = createSector("Setor Atribuir");
    long adminId = adminUserId();
    long userId = createUser("atribuir-setor@ifpe.edu.br");

    var response = allocate(userId, sectorId, adminId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((List<Integer>) response.getBody().get("sectorIds")).containsExactly((int) sectorId);
  }

  @Test
  void allocate_userCanBeAllocatedToMultipleSectors() {
    long sectorId1 = createSector("Setor Multiplo 1");
    long sectorId2 = createSector("Setor Multiplo 2");
    long adminId = adminUserId();
    long userId = createUser("multiplos-setores@ifpe.edu.br");

    allocate(userId, sectorId1, adminId);
    var response = allocate(userId, sectorId2, adminId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((List<Integer>) response.getBody().get("sectorIds"))
        .containsExactlyInAnyOrder((int) sectorId1, (int) sectorId2);
  }

  @Test
  void allocate_idempotent_returnsSameState() {
    long sectorId = createSector("Setor Idempotente");
    long adminId = adminUserId();
    long userId = createUser("idempotente-alocar@ifpe.edu.br");

    allocate(userId, sectorId, adminId);
    var response = allocate(userId, sectorId, adminId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((List<Integer>) response.getBody().get("sectorIds")).containsExactly((int) sectorId);
  }

  @Test
  void allocate_asGestorOfTargetSector_succeeds() {
    long sectorId = createSector("Setor Gestor Mesmo Setor");
    long gestorId = createUserWithProfile("gestor-mesmo-setor@ifpe.edu.br", "GESTOR_SETOR");
    allocateSectorDirectly(gestorId, sectorId);
    long userId = createUser("alocado-por-gestor@ifpe.edu.br");

    var response = allocate(userId, sectorId, gestorId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((List<Integer>) response.getBody().get("sectorIds")).containsExactly((int) sectorId);
  }

  @Test
  void allocate_asGestorOfDifferentSector_returns403() {
    long sectorId = createSector("Setor Alvo Gestor Diferente");
    long outroSectorId = createSector("Outro Setor Gestor Diferente");
    long gestorId = createUserWithProfile("gestor-outro-setor@ifpe.edu.br", "GESTOR_SETOR");
    allocateSectorDirectly(gestorId, outroSectorId);
    long userId = createUser("nao-alocado@ifpe.edu.br");

    var response = allocate(userId, sectorId, gestorId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void allocate_asUserWithoutPermission_returns403() {
    long sectorId = createSector("Setor Sem Permissao Atribuir");
    long consultaId = createUserWithProfile("consulta-atribuir@ifpe.edu.br", "CONSULTA");
    long userId = createUser("alvo-sem-permissao@ifpe.edu.br");

    var response = allocate(userId, sectorId, consultaId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void allocate_userNotFound_returns404() {
    long sectorId = createSector("Setor Usuario Inexistente");
    long adminId = adminUserId();

    var response = allocate(999999, sectorId, adminId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void allocate_sectorNotFound_returns404() {
    long adminId = adminUserId();
    long userId = createUser("setor-inexistente@ifpe.edu.br");

    var response = allocate(userId, 999999, adminId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void deallocate_asAdmin_removesSector() {
    long sectorId = createSector("Setor Desvincular");
    long adminId = adminUserId();
    long userId = createUser("desvincular-setor@ifpe.edu.br");
    allocate(userId, sectorId, adminId);

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
    allocate(userId, sectorId, adminId);
    long gestorId = createUserWithProfile("gestor-desvincular@ifpe.edu.br", "GESTOR_SETOR");
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
    allocate(userId, sectorId, adminId);
    long gestorId = createUserWithProfile("gestor-outro-desvincular@ifpe.edu.br", "GESTOR_SETOR");
    allocateSectorDirectly(gestorId, outroSectorId);

    var response = deallocate(userId, sectorId, gestorId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void assignProfile_returnsUserWithProfileId() {
    long profileId = createProfile("Perfil Atribuir");
    long userId = createUser("atribuir-perfil@ifpe.edu.br");

    var response =
        restTemplate.exchange(
            "/users/" + userId + "/profile",
            HttpMethod.PATCH,
            new HttpEntity<>(Map.of("profileId", profileId), authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("profileId")).isEqualTo((int) profileId);
  }

  @Test
  void assignProfile_userNotFound_returns404() {
    long profileId = createProfile("Perfil Usuario Inexistente");

    var response =
        restTemplate.exchange(
            "/users/999999/profile",
            HttpMethod.PATCH,
            new HttpEntity<>(Map.of("profileId", profileId), authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void assignProfile_profileNotFound_returns400() {
    long userId = createUser("perfil-inexistente@ifpe.edu.br");

    var response =
        restTemplate.exchange(
            "/users/" + userId + "/profile",
            HttpMethod.PATCH,
            new HttpEntity<>(Map.of("profileId", 999999), authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void assignProfile_nullProfileId_unassigns() {
    long profileId = createProfile("Perfil Desvincular");
    long userId = createUser("desvincular-perfil@ifpe.edu.br");

    restTemplate.exchange(
        "/users/" + userId + "/profile",
        HttpMethod.PATCH,
        new HttpEntity<>(Map.of("profileId", profileId), authHeaders()),
        Map.class);

    var body = new HashMap<String, Object>();
    body.put("profileId", null);
    var response =
        restTemplate.exchange(
            "/users/" + userId + "/profile",
            HttpMethod.PATCH,
            new HttpEntity<>(body, authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("profileId")).isNull();
  }

  @Test
  void unauthenticated_returns403() {
    var response = restTemplate.getForEntity("/users", Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
