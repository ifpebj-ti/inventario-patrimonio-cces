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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confere que a migration de seed (permissões, perfis e associações sugeridos no documento de
 * proposta) rodou corretamente. O Testcontainers sobe um Postgres novo por teste, então as
 * migrations (incluindo o seed) rodam do zero a cada execução.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SeedDataE2ETest {

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

    private HttpHeaders authHeaders() {
        Algorithm algorithm = Algorithm.HMAC256(TEST_SECRET);
        String token = JWT.create()
                .withIssuer("inventory")
                .withSubject("1")
                .withExpiresAt(Instant.now().plus(Duration.ofHours(1)))
                .sign(algorithm);

        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private List<Map> listProfiles() {
        var response = restTemplate.exchange(
                "/profiles", HttpMethod.GET, new HttpEntity<>(authHeaders()), List.class);
        return response.getBody();
    }

    private Map findProfileByName(String name) {
        return listProfiles().stream()
                .filter(p -> name.equals(p.get("name")))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void seed_createsThirteenPermissions() {
        var response = restTemplate.exchange(
                "/permissions", HttpMethod.GET, new HttpEntity<>(authHeaders()), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(13);
    }

    @Test
    void seed_createsFourProfiles() {
        var profiles = listProfiles();

        assertThat(profiles).hasSize(4);
        assertThat(profiles).extracting(p -> p.get("name"))
                .containsExactlyInAnyOrder("ADMIN_ORGANIZATION", "GESTOR_SETOR", "OPERADOR_CAMPO", "CONSULTA");
    }

    @Test
    void seed_adminOrganizationHasAllPermissions() {
        var admin = findProfileByName("ADMIN_ORGANIZATION");

        assertThat((List<?>) admin.get("permissionIds")).hasSize(13);
    }

    @Test
    void seed_gestorSetorHasNinePermissions() {
        var gestorSetor = findProfileByName("GESTOR_SETOR");

        assertThat((List<?>) gestorSetor.get("permissionIds")).hasSize(9);
    }

    @Test
    void seed_operadorCampoHasThreePermissions() {
        var operadorCampo = findProfileByName("OPERADOR_CAMPO");

        assertThat((List<?>) operadorCampo.get("permissionIds")).hasSize(3);
    }

    @Test
    void seed_consultaHasNoPermissions() {
        var consulta = findProfileByName("CONSULTA");

        assertThat((List<?>) consulta.get("permissionIds")).isEmpty();
    }
}
