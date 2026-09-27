package clp.inventory.profile;

import clp.inventory.model.User;
import clp.inventory.repository.UserRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
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

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sobe a aplicação inteira (Postgres real via Testcontainers, HTTP real via TestRestTemplate).
 * O JWT é assinado diretamente no teste, mesma abordagem do OrganizationE2ETest/SectorE2ETest.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProfileE2ETest {

    private static final String TEST_SECRET = "test-secret-de-pelo-menos-32-bytes-aqui";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void authProperties(DynamicPropertyRegistry registry) {
        registry.add("security.token.secret", () -> TEST_SECRET);
    }

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository userRepository;

    private String mintToken() {
        Algorithm algorithm = Algorithm.HMAC256(TEST_SECRET);
        return JWT.create()
                .withIssuer("inventory")
                .withSubject("1")
                .withExpiresAt(Instant.now().plus(Duration.ofHours(1)))
                .sign(algorithm);
    }

    private HttpHeaders authHeaders() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(mintToken());
        return headers;
    }

    private Map<String, Object> profileBody(String name, String description) {
        var body = new HashMap<String, Object>();
        body.put("name", name);
        body.put("description", description);
        return body;
    }

    private ResponseEntity<Map> create(String name, String description) {
        return restTemplate.postForEntity(
                "/profiles",
                new HttpEntity<>(profileBody(name, description), authHeaders()),
                Map.class);
    }

    private long createPermission(String name) {
        var body = new HashMap<String, Object>();
        body.put("name", name);
        var response = restTemplate.postForEntity(
                "/permissions", new HttpEntity<>(body, authHeaders()), Map.class);
        return ((Number) response.getBody().get("id")).longValue();
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

        var response = restTemplate.exchange(
                "/profiles", HttpMethod.GET, new HttpEntity<>(authHeaders()), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void getById_returnsProfile() {
        var created = create("Perfil Busca", null);
        long id = ((Number) created.getBody().get("id")).longValue();

        var response = restTemplate.exchange(
                "/profiles/" + id, HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("Perfil Busca");
    }

    @Test
    void getById_notFound_returns404() {
        var response = restTemplate.exchange(
                "/profiles/999999", HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void update_returnsUpdatedProfile() {
        var created = create("Perfil Original", null);
        long id = ((Number) created.getBody().get("id")).longValue();

        var response = restTemplate.exchange(
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

        var response = restTemplate.exchange(
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

        var deleteResponse = restTemplate.exchange(
                "/profiles/" + id, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var getResponse = restTemplate.exchange(
                "/profiles/" + id, HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void delete_notFound_returns404() {
        var response = restTemplate.exchange(
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
        long profileId = ((Number) create("Perfil Com Permissao", null).getBody().get("id")).longValue();
        long permissionId = createPermission("Permissao Associavel");

        var response = restTemplate.exchange(
                "/profiles/" + profileId + "/permissions/" + permissionId,
                HttpMethod.POST, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<Object>) response.getBody().get("permissionIds")).contains((int) permissionId);
    }

    @Test
    void addPermission_idempotent_returnsSameState() {
        long profileId = ((Number) create("Perfil Idempotente", null).getBody().get("id")).longValue();
        long permissionId = createPermission("Permissao Idempotente");

        restTemplate.exchange(
                "/profiles/" + profileId + "/permissions/" + permissionId,
                HttpMethod.POST, new HttpEntity<>(authHeaders()), Map.class);
        var second = restTemplate.exchange(
                "/profiles/" + profileId + "/permissions/" + permissionId,
                HttpMethod.POST, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) second.getBody().get("permissionIds")).hasSize(1);
    }

    @Test
    void addPermission_profileNotFound_returns404() {
        long permissionId = createPermission("Permissao Sem Perfil");

        var response = restTemplate.exchange(
                "/profiles/999999/permissions/" + permissionId,
                HttpMethod.POST, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void addPermission_permissionNotFound_returns404() {
        long profileId = ((Number) create("Perfil Sem Permissao", null).getBody().get("id")).longValue();

        var response = restTemplate.exchange(
                "/profiles/" + profileId + "/permissions/999999",
                HttpMethod.POST, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void removePermission_returnsProfileWithoutPermission() {
        long profileId = ((Number) create("Perfil Para Desassociar", null).getBody().get("id")).longValue();
        long permissionId = createPermission("Permissao Para Desassociar");

        restTemplate.exchange(
                "/profiles/" + profileId + "/permissions/" + permissionId,
                HttpMethod.POST, new HttpEntity<>(authHeaders()), Map.class);

        var response = restTemplate.exchange(
                "/profiles/" + profileId + "/permissions/" + permissionId,
                HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<Object>) response.getBody().get("permissionIds")).doesNotContain((int) permissionId);
    }

    @Test
    void removePermission_notAssociated_isIdempotent() {
        long profileId = ((Number) create("Perfil Sem Associacao", null).getBody().get("id")).longValue();
        long permissionId = createPermission("Permissao Nunca Associada");

        var response = restTemplate.exchange(
                "/profiles/" + profileId + "/permissions/" + permissionId,
                HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Map.class);

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
                "/users/" + user.getId() + "/profile", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("profileId", profileId), authHeaders()), Map.class);

        var response = restTemplate.exchange(
                "/profiles/" + profileId, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
