package clp.inventory.profile;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.dto.ProfileDto;
import clp.inventory.model.User;
import clp.inventory.repository.ProfileRepository;
import clp.inventory.repository.UserRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.time.Instant;
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

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.mail.password=test")
@AutoConfigureTestRestTemplate
class ProfileE2ETest {
  private static final String SECRET = "profile-read-only-test-secret-32-bytes";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("security.token.secret", () -> SECRET);
  }

  @Autowired TestRestTemplate http;
  @Autowired ProfileRepository profiles;
  @Autowired UserRepository users;

  private HttpEntity<?> request(String profileName) {
    User user = new User();
    user.setName("Teste");
    user.setEmail("catalog-" + System.nanoTime() + "@ifpe.edu.br");
    user.setGoogleId(user.getEmail());
    user.setProfile(
        profiles.findAll().stream()
            .filter(p -> p.name().equals(profileName))
            .findFirst()
            .orElseThrow());
    users.save(user);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(
        JWT.create()
            .withIssuer("inventory")
            .withSubject(user.getId().toString())
            .withExpiresAt(Instant.now().plusSeconds(3600))
            .sign(Algorithm.HMAC256(SECRET)));
    return new HttpEntity<>(Map.of("name", "Alterado", "description", "Alterado"), headers);
  }

  @Test
  void adminReadsNativeProfilesAndTheirFixedPermissions() {
    var response =
        http.exchange(
            "/profiles", HttpMethod.GET, request("ADMIN_ORGANIZATION"), ProfileDto[].class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .extracting(ProfileDto::name)
        .containsExactlyInAnyOrder(
            "ADMIN_ORGANIZATION", "GESTOR_SETOR", "OPERADOR_CAMPO", "CONSULTA");
    assertThat(response.getBody())
        .filteredOn(p -> p.name().equals("CONSULTA"))
        .allSatisfy(p -> assertThat(p.permissionIds()).isEmpty());
  }

  @Test
  void mutationsAreRejectedForEveryNativeProfileWithoutChangingCatalog() {
    var auth = request("ADMIN_ORGANIZATION");
    var before = http.exchange("/profiles", HttpMethod.GET, auth, ProfileDto[].class).getBody();
    assertThat(http.exchange("/profiles", HttpMethod.POST, auth, String.class).getStatusCode())
        .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    for (ProfileDto profile : before) {
      String path = "/profiles/" + profile.id();
      for (HttpMethod method :
          new HttpMethod[] {HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE}) {
        assertThat(http.exchange(path, method, auth, String.class).getStatusCode())
            .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
      }
      for (HttpMethod method : new HttpMethod[] {HttpMethod.POST, HttpMethod.DELETE}) {
        assertThat(
                http.exchange(path + "/permissions/1", method, auth, String.class).getStatusCode())
            .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
      }
    }
    var after = http.exchange("/profiles", HttpMethod.GET, auth, ProfileDto[].class).getBody();
    assertThat(after).containsExactlyInAnyOrder(before);
  }

  @Test
  void nonAdminCannotAlterCatalogAndOnlyManagersReadIt() {
    var auth = request("GESTOR_SETOR");
    assertThat(http.exchange("/profiles", HttpMethod.POST, auth, String.class).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    // O gestor lê o catálogo (para mostrar o perfil dos usuários do setor); o que ele pode
    // atribuir é limitado no cadastro e na alteração de perfil.
    var listed = http.exchange("/profiles", HttpMethod.GET, auth, String.class);
    assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(listed.getBody()).contains("OPERADOR_CAMPO", "CONSULTA", "GESTOR_SETOR");
    // Operador e consulta não leem o catálogo.
    for (String name : java.util.List.of("OPERADOR_CAMPO", "CONSULTA")) {
      assertThat(
              http.exchange("/profiles", HttpMethod.GET, request(name), String.class)
                  .getStatusCode())
          .as(name)
          .isEqualTo(HttpStatus.FORBIDDEN);
    }
  }

  @Test
  void missingProfileReturnsNotFoundToAdmin() {
    assertThat(
            http.exchange(
                    "/profiles/999999", HttpMethod.GET, request("ADMIN_ORGANIZATION"), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
