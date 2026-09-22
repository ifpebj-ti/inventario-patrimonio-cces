package clp.inventory.inventory;

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
 * Diferente dos outros testes E2E do módulo, o JWT aqui precisa ter como subject o id de um
 * usuário real: InventoryService.createInventory/updateInventory chamam
 * userService.findUserById, que lança se o id não existir em im_user.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventorySectorE2ETest {

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

    private long createUser(String email) {
        User user = new User();
        user.setName("Test User");
        user.setEmail(email);
        user.setGoogleId("google-" + email);
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

    private HttpHeaders authHeaders(long userId) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(mintToken(userId));
        return headers;
    }

    private long createOrganization(long userId, String name) {
        var body = new HashMap<String, Object>();
        body.put("name", name);
        var response = restTemplate.postForEntity(
                "/organizations", new HttpEntity<>(body, authHeaders(userId)), Map.class);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private long createSector(long userId, String name, long organizationId) {
        var body = new HashMap<String, Object>();
        body.put("name", name);
        body.put("organizationId", organizationId);
        var response = restTemplate.postForEntity(
                "/sectors", new HttpEntity<>(body, authHeaders(userId)), Map.class);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private ResponseEntity<Map> createInventory(long userId, String name, Long sectorId) {
        var body = new HashMap<String, Object>();
        body.put("name", name);
        body.put("description", "Descricao de teste");
        body.put("sectorId", sectorId);
        return restTemplate.postForEntity(
                "/inventory/new-inventory", new HttpEntity<>(body, authHeaders(userId)), Map.class);
    }

    @Test
    void create_returnsInventoryWithSectorId() {
        long userId = createUser("inventario-com-setor@ifpe.edu.br");
        long organizationId = createOrganization(userId, "Organizacao Inventario 1");
        long sectorId = createSector(userId, "Setor Inventario 1", organizationId);

        var response = createInventory(userId, "Inventario Com Setor", sectorId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("sectorId")).isEqualTo((int) sectorId);
    }

    @Test
    void create_withoutSectorId_returnsInventoryWithNullSectorId() {
        long userId = createUser("inventario-sem-setor@ifpe.edu.br");

        var response = createInventory(userId, "Inventario Sem Setor", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("sectorId")).isNull();
    }

    @Test
    void create_sectorNotFound_returns400() {
        long userId = createUser("inventario-setor-inexistente@ifpe.edu.br");

        var body = new HashMap<String, Object>();
        body.put("name", "Inventario Setor Inexistente");
        body.put("sectorId", 999999L);

        // InventoryController devolve um corpo em texto plano (não JSON) para esse erro —
        // diferente dos controllers de Organization/Sector/etc. — por isso String, não Map.
        var response = restTemplate.postForEntity(
                "/inventory/new-inventory", new HttpEntity<>(body, authHeaders(userId)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void update_changesSectorId() {
        long userId = createUser("inventario-atualizar-setor@ifpe.edu.br");
        long organizationId = createOrganization(userId, "Organizacao Inventario Update");
        long sectorId = createSector(userId, "Setor Inventario Update", organizationId);

        var created = createInventory(userId, "Inventario Para Atualizar", null);
        long inventoryId = ((Number) created.getBody().get("id")).longValue();

        var body = new HashMap<String, Object>();
        body.put("name", "Inventario Para Atualizar");
        body.put("description", "Descricao de teste");
        body.put("sectorId", sectorId);

        var response = restTemplate.exchange(
                "/inventory/" + inventoryId, HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(userId)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("sectorId")).isEqualTo((int) sectorId);
    }

    @Test
    void sectorInventories_returnsOnlyThatSectorInventories() {
        long userId = createUser("inventario-listagem-setor@ifpe.edu.br");
        long organizationId = createOrganization(userId, "Organizacao Listagem Inventario");
        long sectorA = createSector(userId, "Setor Listagem A", organizationId);
        long sectorB = createSector(userId, "Setor Listagem B", organizationId);

        createInventory(userId, "Inventario Setor A", sectorA);
        createInventory(userId, "Inventario Setor B", sectorB);

        var response = restTemplate.exchange(
                "/inventory/sector-inventories?sectorId=" + sectorA,
                HttpMethod.GET, new HttpEntity<>(authHeaders(userId)), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void sectorInventories_missingSectorId_returns400() {
        long userId = createUser("inventario-listagem-sem-setor@ifpe.edu.br");

        var response = restTemplate.exchange(
                "/inventory/sector-inventories",
                HttpMethod.GET, new HttpEntity<>(authHeaders(userId)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
