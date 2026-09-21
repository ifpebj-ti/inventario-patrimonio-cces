package clp.inventory.organization;

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
 * O JWT é assinado diretamente no teste, sem passar por /auth/google: os endpoints de
 * Organization não consultam UserRepository, então só a validação de assinatura/issuer do
 * SecurityFilter importa aqui.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrganizationE2ETest {

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

    private Map<String, Object> organizationBody(String name, String acronym, String domain) {
        var body = new HashMap<String, Object>();
        body.put("name", name);
        body.put("acronym", acronym);
        body.put("domain", domain);
        return body;
    }

    private ResponseEntity<Map> create(String name, String acronym, String domain) {
        return restTemplate.postForEntity(
                "/organizations",
                new HttpEntity<>(organizationBody(name, acronym, domain), authHeaders()),
                Map.class);
    }

    @Test
    void create_returnsCreatedOrganization() {
        var response = create("IFPE", "IFPE", "ifpe.edu.br");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("name")).isEqualTo("IFPE");
        assertThat(response.getBody().get("acronym")).isEqualTo("IFPE");
        assertThat(response.getBody().get("domain")).isEqualTo("ifpe.edu.br");
        assertThat(response.getBody().get("id")).isNotNull();
    }

    @Test
    void create_duplicateName_returns400() {
        create("Organizacao Duplicada", null, null);
        var second = create("Organizacao Duplicada", null, null);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_blankName_returns400() {
        var response = create("", null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void list_returnsAllOrganizations() {
        create("Organizacao Listagem 1", null, null);
        create("Organizacao Listagem 2", null, null);

        var response = restTemplate.exchange(
                "/organizations", HttpMethod.GET, new HttpEntity<>(authHeaders()), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void getById_returnsOrganization() {
        var created = create("Organizacao Busca", null, null);
        long id = ((Number) created.getBody().get("id")).longValue();

        var response = restTemplate.exchange(
                "/organizations/" + id, HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("Organizacao Busca");
    }

    @Test
    void getById_notFound_returns404() {
        var response = restTemplate.exchange(
                "/organizations/999999", HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void update_returnsUpdatedOrganization() {
        var created = create("Organizacao Original", null, null);
        long id = ((Number) created.getBody().get("id")).longValue();

        var response = restTemplate.exchange(
                "/organizations/" + id,
                HttpMethod.PUT,
                new HttpEntity<>(organizationBody("Organizacao Atualizada", "OA", "oa.edu.br"), authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("Organizacao Atualizada");
        assertThat(response.getBody().get("acronym")).isEqualTo("OA");
    }

    @Test
    void update_duplicateName_returns400() {
        create("Organizacao A", null, null);
        var orgB = create("Organizacao B", null, null);
        long idB = ((Number) orgB.getBody().get("id")).longValue();

        var response = restTemplate.exchange(
                "/organizations/" + idB,
                HttpMethod.PUT,
                new HttpEntity<>(organizationBody("Organizacao A", null, null), authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void delete_removesOrganization() {
        var created = create("Organizacao Para Remover", null, null);
        long id = ((Number) created.getBody().get("id")).longValue();

        var deleteResponse = restTemplate.exchange(
                "/organizations/" + id, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var getResponse = restTemplate.exchange(
                "/organizations/" + id, HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void delete_notFound_returns404() {
        var response = restTemplate.exchange(
                "/organizations/999999", HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unauthenticated_returns403() {
        // SecurityConfig ainda não tem um AuthenticationEntryPoint customizado (fica um TODO lá),
        // então o Spring Security responde 403 para requisição sem token, não 401.
        var response = restTemplate.getForEntity("/organizations", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void delete_organizationWithSectors_returns409() {
        var created = create("Organizacao Com Setor", null, null);
        long organizationId = ((Number) created.getBody().get("id")).longValue();

        var sectorBody = new HashMap<String, Object>();
        sectorBody.put("name", "Setor Vinculado");
        sectorBody.put("organizationId", organizationId);
        restTemplate.postForEntity("/sectors", new HttpEntity<>(sectorBody, authHeaders()), Map.class);

        var response = restTemplate.exchange(
                "/organizations/" + organizationId, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
