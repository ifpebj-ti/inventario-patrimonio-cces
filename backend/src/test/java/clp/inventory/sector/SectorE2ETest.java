package clp.inventory.sector;

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
 * O JWT é assinado diretamente no teste, mesma abordagem do OrganizationE2ETest.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SectorE2ETest {

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

    private long createOrganization(String name) {
        var body = new HashMap<String, Object>();
        body.put("name", name);
        var response = restTemplate.postForEntity(
                "/organizations", new HttpEntity<>(body, authHeaders()), Map.class);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private Map<String, Object> sectorBody(String name, String code, long organizationId, Long parentSectorId) {
        var body = new HashMap<String, Object>();
        body.put("name", name);
        body.put("code", code);
        body.put("organizationId", organizationId);
        body.put("parentSectorId", parentSectorId);
        return body;
    }

    private ResponseEntity<Map> create(String name, String code, long organizationId, Long parentSectorId) {
        return restTemplate.postForEntity(
                "/sectors",
                new HttpEntity<>(sectorBody(name, code, organizationId, parentSectorId), authHeaders()),
                Map.class);
    }

    @Test
    void create_returnsCreatedSector() {
        long organizationId = createOrganization("Organizacao Setor 1");

        var response = create("TI", "TI", organizationId, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("name")).isEqualTo("TI");
        assertThat(response.getBody().get("organizationId")).isEqualTo((int) organizationId);
        assertThat(response.getBody().get("parentSectorId")).isNull();
    }

    @Test
    void create_withParentSector_returnsCreated() {
        long organizationId = createOrganization("Organizacao Setor 2");
        long parentId = ((Number) create("Diretoria", null, organizationId, null).getBody().get("id")).longValue();

        var response = create("Coordenacao de TI", null, organizationId, parentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("parentSectorId")).isEqualTo((int) parentId);
    }

    @Test
    void create_organizationNotFound_returns400() {
        var response = create("Setor Orfao", null, 999999, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_parentFromDifferentOrganization_returns400() {
        long organizationA = createOrganization("Organizacao A Setor");
        long organizationB = createOrganization("Organizacao B Setor");
        long parentInA = ((Number) create("Setor Raiz A", null, organizationA, null).getBody().get("id")).longValue();

        var response = create("Setor Filho B", null, organizationB, parentInA);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_blankName_returns400() {
        long organizationId = createOrganization("Organizacao Nome Vazio");

        var response = create("", null, organizationId, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_duplicateNameAmongSiblings_returns400() {
        long organizationId = createOrganization("Organizacao Duplicidade Setor");
        create("Setor Duplicado", null, organizationId, null);

        var second = create("Setor Duplicado", null, organizationId, null);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_sameNameDifferentParent_returnsCreated() {
        long organizationId = createOrganization("Organizacao Nomes Repetidos");
        long parentA = ((Number) create("Diretoria A", null, organizationId, null).getBody().get("id")).longValue();
        long parentB = ((Number) create("Diretoria B", null, organizationId, null).getBody().get("id")).longValue();

        var underA = create("Financeiro", null, organizationId, parentA);
        var underB = create("Financeiro", null, organizationId, parentB);

        assertThat(underA.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(underB.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void list_filterByOrganizationId_returnsOnlyThoseSectors() {
        long organizationA = createOrganization("Organizacao Listagem A");
        long organizationB = createOrganization("Organizacao Listagem B");
        create("Setor A1", null, organizationA, null);
        create("Setor B1", null, organizationB, null);

        var response = restTemplate.exchange(
                "/sectors?organizationId=" + organizationA,
                HttpMethod.GET, new HttpEntity<>(authHeaders()), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void getById_returnsSector() {
        long organizationId = createOrganization("Organizacao Busca Setor");
        long id = ((Number) create("Setor Busca", null, organizationId, null).getBody().get("id")).longValue();

        var response = restTemplate.exchange(
                "/sectors/" + id, HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("Setor Busca");
    }

    @Test
    void getById_notFound_returns404() {
        var response = restTemplate.exchange(
                "/sectors/999999", HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void update_returnsUpdatedSector() {
        long organizationId = createOrganization("Organizacao Update Setor");
        long id = ((Number) create("Setor Original", null, organizationId, null).getBody().get("id")).longValue();

        var response = restTemplate.exchange(
                "/sectors/" + id,
                HttpMethod.PUT,
                new HttpEntity<>(sectorBody("Setor Atualizado", "SA", organizationId, null), authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("Setor Atualizado");
        assertThat(response.getBody().get("code")).isEqualTo("SA");
    }

    @Test
    void update_parentToOwnDescendant_returns400() {
        long organizationId = createOrganization("Organizacao Ciclo Setor");
        long rootId = ((Number) create("Raiz", null, organizationId, null).getBody().get("id")).longValue();
        long childId = ((Number) create("Filho", null, organizationId, rootId).getBody().get("id")).longValue();

        var response = restTemplate.exchange(
                "/sectors/" + rootId,
                HttpMethod.PUT,
                new HttpEntity<>(sectorBody("Raiz", null, organizationId, childId), authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void delete_removesSector() {
        long organizationId = createOrganization("Organizacao Delete Setor");
        long id = ((Number) create("Setor Para Remover", null, organizationId, null).getBody().get("id")).longValue();

        var deleteResponse = restTemplate.exchange(
                "/sectors/" + id, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var getResponse = restTemplate.exchange(
                "/sectors/" + id, HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void delete_withChildren_returns409() {
        long organizationId = createOrganization("Organizacao Delete Com Filhos");
        long rootId = ((Number) create("Raiz Com Filho", null, organizationId, null).getBody().get("id")).longValue();
        create("Filho Bloqueador", null, organizationId, rootId);

        var response = restTemplate.exchange(
                "/sectors/" + rootId, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void delete_notFound_returns404() {
        var response = restTemplate.exchange(
                "/sectors/999999", HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unauthenticated_returns403() {
        var response = restTemplate.getForEntity("/sectors", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void delete_sectorWithAssignedUser_returns409() {
        long organizationId = createOrganization("Organizacao Setor Com Usuario");
        long sectorId = ((Number) create("Setor Com Usuario", null, organizationId, null).getBody().get("id")).longValue();

        User user = new User();
        user.setName("Usuario Vinculado");
        user.setEmail("usuario-vinculado-setor@ifpe.edu.br");
        user.setGoogleId("google-usuario-vinculado-setor");
        userRepository.save(user);

        restTemplate.exchange(
                "/users/" + user.getId() + "/sector", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("sectorId", sectorId), authHeaders()), Map.class);

        var response = restTemplate.exchange(
                "/sectors/" + sectorId, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void delete_sectorWithInventory_returns409() {
        long organizationId = createOrganization("Organizacao Setor Com Inventario");
        long sectorId = ((Number) create("Setor Com Inventario", null, organizationId, null).getBody().get("id")).longValue();

        User user = new User();
        user.setName("Usuario Inventario");
        user.setEmail("usuario-inventario-setor@ifpe.edu.br");
        user.setGoogleId("google-usuario-inventario-setor");
        userRepository.save(user);

        Algorithm algorithm = Algorithm.HMAC256(TEST_SECRET);
        String userToken = JWT.create()
                .withIssuer("inventory")
                .withSubject(String.valueOf(user.getId()))
                .withExpiresAt(Instant.now().plus(Duration.ofHours(1)))
                .sign(algorithm);
        var userHeaders = new HttpHeaders();
        userHeaders.setContentType(MediaType.APPLICATION_JSON);
        userHeaders.setBearerAuth(userToken);

        var inventoryBody = new HashMap<String, Object>();
        inventoryBody.put("name", "Inventario Bloqueador");
        inventoryBody.put("sectorId", sectorId);
        restTemplate.postForEntity(
                "/inventory/new-inventory", new HttpEntity<>(inventoryBody, userHeaders), Map.class);

        var response = restTemplate.exchange(
                "/sectors/" + sectorId, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
