package clp.inventory.service;

import static org.assertj.core.api.Assertions.*;

import clp.inventory.dto.UserDto;
import clp.inventory.model.*;
import clp.inventory.repository.*;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.time.Instant;
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

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.mail.password=test"})
@AutoConfigureTestRestTemplate
class AdminUserE2ETest {
  private static final String SECRET = "admin-user-test-secret-at-least-32-bytes";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("security.token.secret", () -> SECRET);
  }

  @Autowired TestRestTemplate http;
  @Autowired UserRepository users;
  @Autowired ProfileRepository profiles;
  @Autowired SectorRepository sectors;
  @Autowired SectorAllocationRepository allocations;

  private User actor(String profileName) {
    User user = new User();
    user.setName("Admin test");
    user.setEmail("actor-" + System.nanoTime() + "@ifpe.edu.br");
    user.setGoogleId(user.getEmail());
    user.setProfile(
        profiles.findAll().stream()
            .filter(p -> p.name().equals(profileName))
            .findFirst()
            .orElseThrow());
    return users.save(user);
  }

  private long profileId(String name) {
    return profiles.findAll().stream()
        .filter(p -> p.name().equals(name))
        .findFirst()
        .orElseThrow()
        .id();
  }

  private HttpEntity<?> request(User actor, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(
        JWT.create()
            .withIssuer("inventory")
            .withSubject(actor.getId().toString())
            .withExpiresAt(Instant.now().plusSeconds(3600))
            .sign(Algorithm.HMAC256(SECRET)));
    return new HttpEntity<>(body, headers);
  }

  private Map<String, Object> body(String email, Long profileId, List<Long> sectorIds) {
    var data = new java.util.HashMap<String, Object>();
    data.put("email", email);
    data.put("profileId", profileId);
    data.put("sectorIds", sectorIds);
    return data;
  }

  @Test
  void createsUserProfileAndMultipleSectorAssociationsAtomically() {
    User admin = actor("ADMIN_ORGANIZATION");
    Sector first = sectors.save(new Sector("First " + System.nanoTime(), null));
    Sector second = sectors.save(new Sector("Second " + System.nanoTime(), null));
    long profileId =
        profiles.findAll().stream()
            .filter(p -> p.name().equals("GESTOR_SETOR"))
            .findFirst()
            .orElseThrow()
            .id();
    String email = "New-" + System.nanoTime() + "@IFPE.edu.br";
    var response =
        http.postForEntity(
            "/users",
            request(admin, body(email, profileId, List.of(first.id(), second.id()))),
            UserDto.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    UserDto dto = response.getBody();
    assertThat(dto.email()).isEqualTo(email.toLowerCase(java.util.Locale.ROOT));
    assertThat(dto.pendingGoogleLogin()).isTrue();
    assertThat(dto.profileId()).isEqualTo(profileId);
    assertThat(dto.sectorIds()).containsExactlyInAnyOrder(first.id(), second.id());
    User stored = users.findById(dto.id()).orElseThrow();
    assertThat(stored.getGoogleId()).isNull();
    assertThat(stored.isSystemUser()).isFalse();
    assertThat(allocations.findByUser_Id(dto.id())).hasSize(2);
    var filtered =
        http.exchange(
            "/users?profileId=" + profileId, HttpMethod.GET, request(admin, null), UserDto[].class);
    assertThat(filtered.getBody()).extracting(UserDto::id).contains(dto.id());
  }

  @Test
  void duplicateEmailIsRejectedRegardlessOfCase() {
    User admin = actor("ADMIN_ORGANIZATION");
    String email = "duplicate-" + System.nanoTime() + "@ifpe.edu.br";
    assertThat(
            http.postForEntity(
                    "/users",
                    request(admin, body(email, profileId("CONSULTA"), List.of())),
                    UserDto.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    assertThat(
            http.postForEntity(
                    "/users",
                    request(
                        admin,
                        body(
                            email.toUpperCase(java.util.Locale.ROOT),
                            profileId("CONSULTA"),
                            List.of())),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void operatorAndConsultCannotCreateUser() {
    for (String profile : List.of("OPERADOR_CAMPO", "CONSULTA")) {
      User actor = actor(profile);
      String email = "forbidden-" + System.nanoTime() + "@ifpe.edu.br";
      assertThat(
              http.postForEntity(
                      "/users",
                      request(actor, body(email, profileId("CONSULTA"), List.of())),
                      String.class)
                  .getStatusCode())
          .as(profile)
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(users.findByEmail(email)).isEmpty();
    }
  }

  @Test
  void profileIsRequiredToCreateUser() {
    User admin = actor("ADMIN_ORGANIZATION");
    String email = "no-profile-" + System.nanoTime() + "@ifpe.edu.br";
    assertThat(
            http.postForEntity("/users", request(admin, body(email, null, List.of())), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(users.findByEmail(email)).isEmpty();
  }

  @Test
  void inactiveSectorRejectsEntireCreation() {
    User admin = actor("ADMIN_ORGANIZATION");
    Sector inactive = new Sector("Inactive " + System.nanoTime(), null);
    inactive.setActive(false);
    sectors.save(inactive);
    String email = "inactive-" + System.nanoTime() + "@ifpe.edu.br";
    assertThat(
            http.postForEntity(
                    "/users",
                    request(admin, body(email, profileId("CONSULTA"), List.of(inactive.id()))),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(users.findByEmail(email)).isEmpty();
  }

  @Test
  void invalidEmailIsRejectedWithoutCreatingUser() {
    User admin = actor("ADMIN_ORGANIZATION");
    assertThat(
            http.postForEntity(
                    "/users", request(admin, body("invalid-email", null, List.of())), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(users.findByEmail("invalid-email")).isEmpty();
  }
}
