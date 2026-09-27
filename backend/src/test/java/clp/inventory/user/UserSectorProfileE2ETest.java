package clp.inventory.user;

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
 * Como User só nasce via /auth/google em produção, o usuário de teste é inserido direto via
 * UserRepository — mais simples que subir o servidor JWKS fake só para ter uma linha em im_user.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserSectorProfileE2ETest {

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

    private HttpHeaders authHeaders() {
        Algorithm algorithm = Algorithm.HMAC256(TEST_SECRET);
        String token = JWT.create()
                .withIssuer("inventory")
                .withSubject("1")
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

    private long createOrganization(String name) {
        var body = new HashMap<String, Object>();
        body.put("name", name);
        var response = restTemplate.postForEntity(
                "/organizations", new HttpEntity<>(body, authHeaders()), Map.class);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private long createSector(String name, long organizationId) {
        var body = new HashMap<String, Object>();
        body.put("name", name);
        body.put("organizationId", organizationId);
        var response = restTemplate.postForEntity(
                "/sectors", new HttpEntity<>(body, authHeaders()), Map.class);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private long createProfile(String name) {
        var body = new HashMap<String, Object>();
        body.put("name", name);
        var response = restTemplate.postForEntity(
                "/profiles", new HttpEntity<>(body, authHeaders()), Map.class);
        return ((Number) response.getBody().get("id")).longValue();
    }

    @Test
    void list_returnsAllUsers() {
        createUser("list1@ifpe.edu.br");
        createUser("list2@ifpe.edu.br");

        var response = restTemplate.exchange(
                "/users", HttpMethod.GET, new HttpEntity<>(authHeaders()), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void list_filterBySectorId_returnsOnlyUsersInThatSector() {
        long organizationId = createOrganization("Organizacao Filtro Usuario Setor");
        long sectorId = createSector("Setor Filtro Usuario", organizationId);
        long userId = createUser("filtro-setor@ifpe.edu.br");
        createUser("sem-setor@ifpe.edu.br");

        restTemplate.exchange(
                "/users/" + userId + "/sector", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("sectorId", sectorId), authHeaders()), Map.class);

        var response = restTemplate.exchange(
                "/users?sectorId=" + sectorId, HttpMethod.GET, new HttpEntity<>(authHeaders()), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void list_filterByProfileId_returnsOnlyUsersWithThatProfile() {
        long profileId = createProfile("Perfil Filtro Usuario");
        long userId = createUser("filtro-perfil@ifpe.edu.br");
        createUser("sem-perfil@ifpe.edu.br");

        restTemplate.exchange(
                "/users/" + userId + "/profile", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("profileId", profileId), authHeaders()), Map.class);

        var response = restTemplate.exchange(
                "/users?profileId=" + profileId, HttpMethod.GET, new HttpEntity<>(authHeaders()), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void assignSector_returnsUserWithSectorId() {
        long organizationId = createOrganization("Organizacao Atribuir Setor");
        long sectorId = createSector("Setor Atribuir", organizationId);
        long userId = createUser("atribuir-setor@ifpe.edu.br");

        var response = restTemplate.exchange(
                "/users/" + userId + "/sector", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("sectorId", sectorId), authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("sectorId")).isEqualTo((int) sectorId);
    }

    @Test
    void assignSector_userNotFound_returns404() {
        long organizationId = createOrganization("Organizacao Setor Usuario Inexistente");
        long sectorId = createSector("Setor Usuario Inexistente", organizationId);

        var response = restTemplate.exchange(
                "/users/999999/sector", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("sectorId", sectorId), authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void assignSector_sectorNotFound_returns400() {
        long userId = createUser("setor-inexistente@ifpe.edu.br");

        var response = restTemplate.exchange(
                "/users/" + userId + "/sector", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("sectorId", 999999), authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void assignSector_nullSectorId_unassigns() {
        long organizationId = createOrganization("Organizacao Desvincular Setor");
        long sectorId = createSector("Setor Desvincular", organizationId);
        long userId = createUser("desvincular-setor@ifpe.edu.br");

        restTemplate.exchange(
                "/users/" + userId + "/sector", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("sectorId", sectorId), authHeaders()), Map.class);

        var body = new HashMap<String, Object>();
        body.put("sectorId", null);
        var response = restTemplate.exchange(
                "/users/" + userId + "/sector", HttpMethod.PATCH,
                new HttpEntity<>(body, authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("sectorId")).isNull();
    }

    @Test
    void assignProfile_returnsUserWithProfileId() {
        long profileId = createProfile("Perfil Atribuir");
        long userId = createUser("atribuir-perfil@ifpe.edu.br");

        var response = restTemplate.exchange(
                "/users/" + userId + "/profile", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("profileId", profileId), authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("profileId")).isEqualTo((int) profileId);
    }

    @Test
    void assignProfile_userNotFound_returns404() {
        long profileId = createProfile("Perfil Usuario Inexistente");

        var response = restTemplate.exchange(
                "/users/999999/profile", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("profileId", profileId), authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void assignProfile_profileNotFound_returns400() {
        long userId = createUser("perfil-inexistente@ifpe.edu.br");

        var response = restTemplate.exchange(
                "/users/" + userId + "/profile", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("profileId", 999999), authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void assignProfile_nullProfileId_unassigns() {
        long profileId = createProfile("Perfil Desvincular");
        long userId = createUser("desvincular-perfil@ifpe.edu.br");

        restTemplate.exchange(
                "/users/" + userId + "/profile", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("profileId", profileId), authHeaders()), Map.class);

        var body = new HashMap<String, Object>();
        body.put("profileId", null);
        var response = restTemplate.exchange(
                "/users/" + userId + "/profile", HttpMethod.PATCH,
                new HttpEntity<>(body, authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("profileId")).isNull();
    }

    @Test
    void unauthenticated_returns403() {
        var response = restTemplate.getForEntity("/users", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
