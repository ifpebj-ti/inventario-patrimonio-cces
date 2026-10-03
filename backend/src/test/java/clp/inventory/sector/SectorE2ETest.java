package clp.inventory.sector;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Sobe a aplicação inteira (Postgres real via Testcontainers, HTTP real via TestRestTemplate). O
 * JWT é assinado diretamente no teste, sem passar por /auth/google: os endpoints de Sector não
 * consultam UserRepository, então só a validação de assinatura/issuer do SecurityFilter importa
 * aqui.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SectorE2ETest {

  private static final String TEST_SECRET = "test-secret-de-pelo-menos-32-bytes-aqui";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void authProperties(DynamicPropertyRegistry registry) {
    registry.add("security.token.secret", () -> TEST_SECRET);
  }

  @Autowired TestRestTemplate restTemplate;

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

  private Map<String, Object> sectorBody(String name, String code) {
    var body = new HashMap<String, Object>();
    body.put("name", name);
    body.put("code", code);
    return body;
  }

  private ResponseEntity<Map> create(String name, String code) {
    return restTemplate.postForEntity(
        "/sectors", new HttpEntity<>(sectorBody(name, code), authHeaders()), Map.class);
  }

  @Test
  void create_returnsCreatedSector() {
    var response = create("Patrimonio", "PAT");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().get("name")).isEqualTo("Patrimonio");
    assertThat(response.getBody().get("code")).isEqualTo("PAT");
    assertThat(response.getBody().get("active")).isEqualTo(true);
    assertThat(response.getBody().get("id")).isNotNull();
  }

  @Test
  void create_blankName_returns400() {
    var response = create("", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void create_duplicateName_returns400() {
    create("Setor Duplicado", null);
    var second = create("Setor Duplicado", null);

    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void list_returnsAllSectors() {
    create("Setor Listagem 1", null);
    create("Setor Listagem 2", null);

    var response =
        restTemplate.exchange(
            "/sectors", HttpMethod.GET, new HttpEntity<>(authHeaders()), List.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotEmpty();
  }

  @Test
  void getById_returnsSector() {
    var created = create("Setor Busca", null);
    long id = ((Number) created.getBody().get("id")).longValue();

    var response =
        restTemplate.exchange(
            "/sectors/" + id, HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("name")).isEqualTo("Setor Busca");
  }

  @Test
  void getById_notFound_returns404() {
    var response =
        restTemplate.exchange(
            "/sectors/999999", HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void update_returnsUpdatedSector() {
    var created = create("Setor Original", null);
    long id = ((Number) created.getBody().get("id")).longValue();

    var response =
        restTemplate.exchange(
            "/sectors/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(sectorBody("Setor Atualizado", "ATU"), authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("name")).isEqualTo("Setor Atualizado");
    assertThat(response.getBody().get("code")).isEqualTo("ATU");
  }

  @Test
  void update_duplicateName_returns400() {
    create("Setor A", null);
    var setorB = create("Setor B", null);
    long idB = ((Number) setorB.getBody().get("id")).longValue();

    var response =
        restTemplate.exchange(
            "/sectors/" + idB,
            HttpMethod.PUT,
            new HttpEntity<>(sectorBody("Setor A", null), authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void deactivate_returnsDeactivatedSector() {
    var created = create("Setor Para Desativar", null);
    long id = ((Number) created.getBody().get("id")).longValue();

    var response =
        restTemplate.exchange(
            "/sectors/" + id + "/deactivate",
            HttpMethod.PATCH,
            new HttpEntity<>(authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("active")).isEqualTo(false);
  }

  @Test
  void activate_returnsActivatedSector() {
    var created = create("Setor Para Reativar", null);
    long id = ((Number) created.getBody().get("id")).longValue();
    restTemplate.exchange(
        "/sectors/" + id + "/deactivate",
        HttpMethod.PATCH,
        new HttpEntity<>(authHeaders()),
        Map.class);

    var response =
        restTemplate.exchange(
            "/sectors/" + id + "/activate",
            HttpMethod.PATCH,
            new HttpEntity<>(authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("active")).isEqualTo(true);
  }

  @Test
  void deactivate_notFound_returns404() {
    var response =
        restTemplate.exchange(
            "/sectors/999999/deactivate",
            HttpMethod.PATCH,
            new HttpEntity<>(authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void activate_notFound_returns404() {
    var response =
        restTemplate.exchange(
            "/sectors/999999/activate",
            HttpMethod.PATCH,
            new HttpEntity<>(authHeaders()),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void unauthenticated_returns403() {
    // SecurityConfig ainda não tem um AuthenticationEntryPoint customizado (fica um TODO lá),
    // então o Spring Security responde 403 para requisição sem token, não 401.
    var response = restTemplate.getForEntity("/sectors", Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
