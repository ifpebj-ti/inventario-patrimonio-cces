package clp.inventory.profile;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.model.Permission;
import clp.inventory.model.User;
import clp.inventory.repository.PermissionRepository;
import clp.inventory.repository.ProfileRepository;
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
 * Sobe a aplicação inteira (Postgres real via Testcontainers, HTTP real via TestRestTemplate). O
 * JWT é assinado diretamente no teste, mesma abordagem do SectorE2ETest. Todas as rotas de
 * /profiles exigem ADMIN, e o perfil ADMIN_ORGANIZATION é protegido (não renomeia, não exclui e não
 * perde a permissão ADMIN).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ProfileE2ETest {

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

  @Autowired PermissionRepository permissionRepository;

  private long createUserWithProfile(String profileName) {
    var profile =
        profileRepository.findAll().stream()
            .filter(p -> profileName.equals(p.name()))
            .findFirst()
            .orElseThrow();
    User user = new User();
    user.setName("Test User");
    user.setEmail(profileName.toLowerCase() + "-" + System.nanoTime() + "@ifpe.edu.br");
    user.setGoogleId("google-" + profileName + "-" + System.nanoTime());
    user.setProfile(profile);
    return userRepository.save(user).getId();
  }

  private String mintToken(long userId) {
    Algorithm algorithm = Algorithm.HMAC256(TEST_SECRET);
    return JWT.create()
        .withIssuer("inventory")
        .withSubject(String.valueOf(userId))
        .withExpiresAt(Instant.now().plus(Duration.ofHours(1)))
        .sign(algorithm);
  }

  private HttpHeaders headersFor(long userId) {
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(mintToken(userId));
    return headers;
  }

  private HttpHeaders authHeaders() {
    return headersFor(createUserWithProfile("ADMIN_ORGANIZATION"));
  }

  private Map<String, Object> profileBody(String name, String description) {
    var body = new HashMap<String, Object>();
    body.put("name", name);
    body.put("description", description);
    return body;
  }

  private ResponseEntity<Map> create(String name, String description) {
    return restTemplate.postForEntity(
        "/profiles", new HttpEntity<>(profileBody(name, description), authHeaders()), Map.class);
  }

  // POST /permissions não existe mais: a permissão de teste entra direto pelo repositório.
  private long createPermission(String name) {
    return permissionRepository.save(new Permission(name, null)).id();
  }

  private Map<String, Object> seededProfile(String name) {
    var response =
        restTemplate.exchange(
            "/profiles", HttpMethod.GET, new HttpEntity<>(authHeaders()), List.class);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> profiles = (List<Map<String, Object>>) response.getBody();
    return profiles.stream().filter(p -> name.equals(p.get("name"))).findFirst().orElseThrow();
  }

  private long seededPermissionId(String name) {
    return permissionRepository.findAll().stream()
        .filter(p -> name.equals(p.name()))
        .findFirst()
        .orElseThrow()
        .id();
  }

  @Test
  void create_returnsCreatedProfile() {
    var response = create("ADMIN", "Administra a organizacao inteira");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().get("name")).isEqualTo("ADMIN");
    assertThat(response.getBody().get("description")).isEqualTo("Administra a organizacao inteira");
    assertThat(response.getBody().get("id")).isNotNull();
  }

  @Test
  void create_duplicateName_returns400() {
    create("Perfil Duplicado", null);
    var second = create("Perfil Duplicado", null);

    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void create_blankName_returns400() {
    var response = create("", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void list_returnsAllProfiles() {
    create("Perfil Listagem 1", null);
    create("Perfil Listagem 2", null);

    var response =
        restTemplate.exchange(
            "/profiles", HttpMethod.GET, new HttpEntity<>(authHeaders()), List.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotEmpty();
  }

  @Test
  void getById_returnsProfile() {
    var created = create("Perfil Busca", null);
    long id = ((Number) created.getBody().get("id")).longValue();

    var response =
        restTemplate.exchange(
            "/profiles/" + id, HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("name")).isEqualTo("Perfil Busca");
  }

  @Test
  void getById_notFound_returns404() {
    var response =
        restTemplate.exchange(
            "/profiles/999999", HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void update_returnsUpdatedProfile() {
    var created = create("Perfil Original", null);
    long id = ((Number) created.getBody().get("id")).longValue();

    var response =
        restTemplate.exchange(
            "/profiles/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(profileBody("Perfil Atualizado", "Nova descricao"), authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("name")).isEqualTo("Perfil Atualizado");
    assertThat(response.getBody().get("description")).isEqualTo("Nova descricao");
  }

  @Test
  void update_duplicateName_returns400() {
    create("Perfil A", null);
    var profileB = create("Perfil B", null);
    long idB = ((Number) profileB.getBody().get("id")).longValue();

    var response =
        restTemplate.exchange(
            "/profiles/" + idB,
            HttpMethod.PUT,
            new HttpEntity<>(profileBody("Perfil A", null), authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void delete_removesProfile() {
    var created = create("Perfil Para Remover", null);
    long id = ((Number) created.getBody().get("id")).longValue();

    var deleteResponse =
        restTemplate.exchange(
            "/profiles/" + id, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Void.class);
    assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    var getResponse =
        restTemplate.exchange(
            "/profiles/" + id, HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void delete_notFound_returns404() {
    var response =
        restTemplate.exchange(
            "/profiles/999999", HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void unauthenticated_returns403() {
    var response = restTemplate.getForEntity("/profiles", Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void addPermission_returnsProfileWithPermission() {
    long profileId =
        ((Number) create("Perfil Com Permissao", null).getBody().get("id")).longValue();
    long permissionId = createPermission("Permissao Associavel");

    var response =
        restTemplate.exchange(
            "/profiles/" + profileId + "/permissions/" + permissionId,
            HttpMethod.POST,
            new HttpEntity<>(authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((List<Object>) response.getBody().get("permissionIds")).contains((int) permissionId);
  }

  @Test
  void addPermission_idempotent_returnsSameState() {
    long profileId = ((Number) create("Perfil Idempotente", null).getBody().get("id")).longValue();
    long permissionId = createPermission("Permissao Idempotente");

    restTemplate.exchange(
        "/profiles/" + profileId + "/permissions/" + permissionId,
        HttpMethod.POST,
        new HttpEntity<>(authHeaders()),
        Map.class);
    var second =
        restTemplate.exchange(
            "/profiles/" + profileId + "/permissions/" + permissionId,
            HttpMethod.POST,
            new HttpEntity<>(authHeaders()),
            Map.class);

    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((List<?>) second.getBody().get("permissionIds")).hasSize(1);
  }

  @Test
  void addPermission_profileNotFound_returns404() {
    long permissionId = createPermission("Permissao Sem Perfil");

    var response =
        restTemplate.exchange(
            "/profiles/999999/permissions/" + permissionId,
            HttpMethod.POST,
            new HttpEntity<>(authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void addPermission_permissionNotFound_returns404() {
    long profileId =
        ((Number) create("Perfil Sem Permissao", null).getBody().get("id")).longValue();

    var response =
        restTemplate.exchange(
            "/profiles/" + profileId + "/permissions/999999",
            HttpMethod.POST,
            new HttpEntity<>(authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void removePermission_returnsProfileWithoutPermission() {
    long profileId =
        ((Number) create("Perfil Para Desassociar", null).getBody().get("id")).longValue();
    long permissionId = createPermission("Permissao Para Desassociar");

    restTemplate.exchange(
        "/profiles/" + profileId + "/permissions/" + permissionId,
        HttpMethod.POST,
        new HttpEntity<>(authHeaders()),
        Map.class);

    var response =
        restTemplate.exchange(
            "/profiles/" + profileId + "/permissions/" + permissionId,
            HttpMethod.DELETE,
            new HttpEntity<>(authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((List<Object>) response.getBody().get("permissionIds"))
        .doesNotContain((int) permissionId);
  }

  @Test
  void removePermission_notAssociated_isIdempotent() {
    long profileId =
        ((Number) create("Perfil Sem Associacao", null).getBody().get("id")).longValue();
    long permissionId = createPermission("Permissao Nunca Associada");

    var response =
        restTemplate.exchange(
            "/profiles/" + profileId + "/permissions/" + permissionId,
            HttpMethod.DELETE,
            new HttpEntity<>(authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void delete_profileWithAssignedUser_returns409() {
    long profileId = ((Number) create("Perfil Com Usuario", null).getBody().get("id")).longValue();

    User user = new User();
    user.setName("Usuario Vinculado");
    user.setEmail("usuario-vinculado-perfil@ifpe.edu.br");
    user.setGoogleId("google-usuario-vinculado-perfil");
    userRepository.save(user);

    restTemplate.exchange(
        "/users/" + user.getId() + "/profile",
        HttpMethod.PATCH,
        new HttpEntity<>(Map.of("profileId", profileId), authHeaders()),
        Map.class);

    var response =
        restTemplate.exchange(
            "/profiles/" + profileId,
            HttpMethod.DELETE,
            new HttpEntity<>(authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  // --- Autorização ---

  @Test
  void everyRoute_asNonAdmin_returns403() {
    for (String profileName : List.of("CONSULTA", "GESTOR_SETOR", "OPERADOR_CAMPO")) {
      var headers = headersFor(createUserWithProfile(profileName));
      var body = profileBody("Nao Deveria Existir", null);
      var withBody = new HttpEntity<>(body, headers);
      var noBody = new HttpEntity<>(headers);

      assertThat(restTemplate.postForEntity("/profiles", withBody, Map.class).getStatusCode())
          .as(profileName + " POST /profiles")
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(
              restTemplate.exchange("/profiles", HttpMethod.GET, noBody, Map.class).getStatusCode())
          .as(profileName + " GET /profiles")
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(
              restTemplate
                  .exchange("/profiles/1", HttpMethod.GET, noBody, Map.class)
                  .getStatusCode())
          .as(profileName + " GET /profiles/1")
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(
              restTemplate
                  .exchange("/profiles/1", HttpMethod.PUT, withBody, Map.class)
                  .getStatusCode())
          .as(profileName + " PUT /profiles/1")
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(
              restTemplate
                  .exchange("/profiles/1", HttpMethod.DELETE, noBody, Map.class)
                  .getStatusCode())
          .as(profileName + " DELETE /profiles/1")
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(
              restTemplate
                  .exchange("/profiles/1/permissions/1", HttpMethod.POST, noBody, Map.class)
                  .getStatusCode())
          .as(profileName + " POST /profiles/1/permissions/1")
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(
              restTemplate
                  .exchange("/profiles/1/permissions/1", HttpMethod.DELETE, noBody, Map.class)
                  .getStatusCode())
          .as(profileName + " DELETE /profiles/1/permissions/1")
          .isEqualTo(HttpStatus.FORBIDDEN);
    }
  }

  @Test
  void nonAdmin_cannotChangeAnything_evenWhenTheTargetExists() {
    long profileId = ((Number) create("Perfil Intocavel", null).getBody().get("id")).longValue();
    var consulta = headersFor(createUserWithProfile("CONSULTA"));

    var update =
        restTemplate.exchange(
            "/profiles/" + profileId,
            HttpMethod.PUT,
            new HttpEntity<>(profileBody("Renomeado Sem Permissao", null), consulta),
            Map.class);
    var delete =
        restTemplate.exchange(
            "/profiles/" + profileId, HttpMethod.DELETE, new HttpEntity<>(consulta), Map.class);

    assertThat(update.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(profileRepository.findById(profileId).orElseThrow().name())
        .isEqualTo("Perfil Intocavel");
  }

  // --- Perfil de administrador protegido ---

  @Test
  void adminProfile_cannotBeRenamed() {
    var admin = seededProfile("ADMIN_ORGANIZATION");
    long id = ((Number) admin.get("id")).longValue();

    var response =
        restTemplate.exchange(
            "/profiles/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(profileBody("OUTRO_NOME", null), authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(profileRepository.findById(id).orElseThrow().name()).isEqualTo("ADMIN_ORGANIZATION");
  }

  @Test
  void adminProfile_descriptionCanStillBeEdited() {
    var admin = seededProfile("ADMIN_ORGANIZATION");
    long id = ((Number) admin.get("id")).longValue();

    var response =
        restTemplate.exchange(
            "/profiles/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(profileBody("ADMIN_ORGANIZATION", "Administra tudo"), authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("description")).isEqualTo("Administra tudo");
  }

  @Test
  void adminProfile_cannotBeDeleted() {
    var admin = seededProfile("ADMIN_ORGANIZATION");
    long id = ((Number) admin.get("id")).longValue();

    var response =
        restTemplate.exchange(
            "/profiles/" + id, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(profileRepository.existsById(id)).isTrue();
  }

  @Test
  void adminProfile_cannotLoseTheAdminPermission() {
    var admin = seededProfile("ADMIN_ORGANIZATION");
    long id = ((Number) admin.get("id")).longValue();
    long adminPermissionId = seededPermissionId("ADMIN");

    var response =
        restTemplate.exchange(
            "/profiles/" + id + "/permissions/" + adminPermissionId,
            HttpMethod.DELETE,
            new HttpEntity<>(authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    var after =
        restTemplate.exchange(
            "/profiles/" + id, HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);
    assertThat((List<Object>) after.getBody().get("permissionIds"))
        .contains((int) adminPermissionId);
  }

  @Test
  void adminProfile_canReceiveExtraPermissions_andLoseThemAgain() {
    var admin = seededProfile("ADMIN_ORGANIZATION");
    long id = ((Number) admin.get("id")).longValue();
    long extraPermissionId = createPermission("Permissao Extra Do Admin");

    var added =
        restTemplate.exchange(
            "/profiles/" + id + "/permissions/" + extraPermissionId,
            HttpMethod.POST,
            new HttpEntity<>(authHeaders()),
            Map.class);
    var removed =
        restTemplate.exchange(
            "/profiles/" + id + "/permissions/" + extraPermissionId,
            HttpMethod.DELETE,
            new HttpEntity<>(authHeaders()),
            Map.class);

    assertThat(added.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((List<Object>) removed.getBody().get("permissionIds"))
        .contains((int) seededPermissionId("ADMIN"));
  }

  @Test
  void otherSeededProfiles_canStillBeEdited() {
    var gestor = seededProfile("GESTOR_SETOR");
    long id = ((Number) gestor.get("id")).longValue();

    var response =
        restTemplate.exchange(
            "/profiles/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(profileBody("GESTOR_SETOR", "Gestor do setor"), authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  // --- Exclusão ---

  @Test
  void delete_profileWithPermissionsAndNoUser_returns204_andKeepsThePermissions() {
    long profileId =
        ((Number) create("Perfil Com Permissao Livre", null).getBody().get("id")).longValue();
    long permissionId = createPermission("Permissao Que Sobrevive");
    restTemplate.exchange(
        "/profiles/" + profileId + "/permissions/" + permissionId,
        HttpMethod.POST,
        new HttpEntity<>(authHeaders()),
        Map.class);

    var response =
        restTemplate.exchange(
            "/profiles/" + profileId,
            HttpMethod.DELETE,
            new HttpEntity<>(authHeaders()),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(profileRepository.existsById(profileId)).isFalse();
    assertThat(permissionRepository.existsById(permissionId)).isTrue();
  }

  @Test
  void delete_profileInUse_returns409_andKeepsTheProfileAndItsPermissions() {
    long profileId =
        ((Number) create("Perfil Em Uso Com Permissao", null).getBody().get("id")).longValue();
    long permissionId = createPermission("Permissao Do Perfil Em Uso");
    restTemplate.exchange(
        "/profiles/" + profileId + "/permissions/" + permissionId,
        HttpMethod.POST,
        new HttpEntity<>(authHeaders()),
        Map.class);
    User user = new User();
    user.setName("Usuario Com Perfil");
    user.setEmail("usuario-perfil-em-uso@ifpe.edu.br");
    user.setGoogleId("google-usuario-perfil-em-uso");
    user.setProfile(profileRepository.findById(profileId).orElseThrow());
    userRepository.save(user);

    var response =
        restTemplate.exchange(
            "/profiles/" + profileId,
            HttpMethod.DELETE,
            new HttpEntity<>(authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    var after =
        restTemplate.exchange(
            "/profiles/" + profileId, HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);
    assertThat(after.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((List<Object>) after.getBody().get("permissionIds")).contains((int) permissionId);
  }

  @Test
  void delete_profileHeldOnlyByTheSystemAccount_returns409() {
    long profileId =
        ((Number) create("Perfil Da Conta De Sistema", null).getBody().get("id")).longValue();
    User system = new User();
    system.setName("System Account");
    system.setEmail("sistema-" + System.nanoTime());
    system.setSystemUser(true);
    system.setProfile(profileRepository.findById(profileId).orElseThrow());
    userRepository.save(system);

    var response =
        restTemplate.exchange(
            "/profiles/" + profileId,
            HttpMethod.DELETE,
            new HttpEntity<>(authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(profileRepository.existsById(profileId)).isTrue();
  }

  // --- Validação ---

  @Test
  void create_nameThatOnlyDiffersBySurroundingSpaces_returns400_not500() {
    create("Perfil Com Espaco", null);

    var response = create("Perfil Com Espaco  ", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void update_nameThatOnlyDiffersBySurroundingSpaces_returns400_not500() {
    create("Perfil Original Espaco", null);
    long otherId = ((Number) create("Perfil Outro Espaco", null).getBody().get("id")).longValue();

    var response =
        restTemplate.exchange(
            "/profiles/" + otherId,
            HttpMethod.PUT,
            new HttpEntity<>(profileBody(" Perfil Original Espaco ", null), authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void create_nameLongerThan100_returns400() {
    assertThat(create("x".repeat(101), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void create_descriptionLongerThan255_returns400() {
    assertThat(create("Perfil Descricao Longa", "d".repeat(256)).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void create_missingName_returns400() {
    var response =
        restTemplate.postForEntity(
            "/profiles", new HttpEntity<>(new HashMap<String, Object>(), authHeaders()), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void create_savesTheTrimmedName() {
    var response = create("  Perfil Aparado  ", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().get("name")).isEqualTo("Perfil Aparado");
  }
}
