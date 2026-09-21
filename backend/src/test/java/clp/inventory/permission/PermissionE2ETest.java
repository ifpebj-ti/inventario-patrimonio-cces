package clp.inventory.permission;

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
 * O JWT é assinado diretamente no teste, mesma abordagem dos demais testes E2E do módulo.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PermissionE2ETest {

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

    private Map<String, Object> permissionBody(String name, String description) {
        var body = new HashMap<String, Object>();
        body.put("name", name);
        body.put("description", description);
        return body;
    }

    private ResponseEntity<Map> create(String name, String description) {
        return restTemplate.postForEntity(
                "/permissions",
                new HttpEntity<>(permissionBody(name, description), authHeaders()),
                Map.class);
    }

    @Test
    void create_returnsCreatedPermission() {
        var response = create("CUSTOM_PERMISSION", "Descricao de teste");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("name")).isEqualTo("CUSTOM_PERMISSION");
        assertThat(response.getBody().get("description")).isEqualTo("Descricao de teste");
        assertThat(response.getBody().get("id")).isNotNull();
    }

    @Test
    void create_duplicateName_returns400() {
        create("Permissao Duplicada", null);
        var second = create("Permissao Duplicada", null);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_blankName_returns400() {
        var response = create("", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void list_returnsAllPermissions() {
        create("Permissao Listagem 1", null);
        create("Permissao Listagem 2", null);

        var response = restTemplate.exchange(
                "/permissions", HttpMethod.GET, new HttpEntity<>(authHeaders()), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void getById_returnsPermission() {
        var created = create("Permissao Busca", null);
        long id = ((Number) created.getBody().get("id")).longValue();

        var response = restTemplate.exchange(
                "/permissions/" + id, HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("Permissao Busca");
    }

    @Test
    void getById_notFound_returns404() {
        var response = restTemplate.exchange(
                "/permissions/999999", HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void update_returnsUpdatedPermission() {
        var created = create("Permissao Original", null);
        long id = ((Number) created.getBody().get("id")).longValue();

        var response = restTemplate.exchange(
                "/permissions/" + id,
                HttpMethod.PUT,
                new HttpEntity<>(permissionBody("Permissao Atualizada", "Nova descricao"), authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("Permissao Atualizada");
        assertThat(response.getBody().get("description")).isEqualTo("Nova descricao");
    }

    @Test
    void update_duplicateName_returns400() {
        create("Permissao A", null);
        var permissionB = create("Permissao B", null);
        long idB = ((Number) permissionB.getBody().get("id")).longValue();

        var response = restTemplate.exchange(
                "/permissions/" + idB,
                HttpMethod.PUT,
                new HttpEntity<>(permissionBody("Permissao A", null), authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void delete_removesPermission() {
        var created = create("Permissao Para Remover", null);
        long id = ((Number) created.getBody().get("id")).longValue();

        var deleteResponse = restTemplate.exchange(
                "/permissions/" + id, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var getResponse = restTemplate.exchange(
                "/permissions/" + id, HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void delete_notFound_returns404() {
        var response = restTemplate.exchange(
                "/permissions/999999", HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void delete_permissionInUse_returns409() {
        var profileBody = new HashMap<String, Object>();
        profileBody.put("name", "Perfil Com Permissao");
        var profile = restTemplate.postForEntity(
                "/profiles", new HttpEntity<>(profileBody, authHeaders()), Map.class);
        long profileId = ((Number) profile.getBody().get("id")).longValue();

        var permission = create("Permissao Em Uso", null);
        long permissionId = ((Number) permission.getBody().get("id")).longValue();

        restTemplate.exchange(
                "/profiles/" + profileId + "/permissions/" + permissionId,
                HttpMethod.POST, new HttpEntity<>(authHeaders()), Map.class);

        var response = restTemplate.exchange(
                "/permissions/" + permissionId, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void unauthenticated_returns403() {
        var response = restTemplate.getForEntity("/permissions", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
