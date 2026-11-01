package clp.inventory.auth;

import static org.assertj.core.api.Assertions.assertThat;

import clp.inventory.model.User;
import clp.inventory.repository.InventoryRepository;
import clp.inventory.repository.PermissionRepository;
import clp.inventory.repository.RoleRepository;
import clp.inventory.repository.SectorAllocationRepository;
import clp.inventory.repository.SectorRepository;
import clp.inventory.repository.UserRepository;
import clp.inventory.service.auth.TestAccountsInitializer;
import java.util.Map;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sem nenhuma senha de conta de teste configurada (o padrão, inclusive em produção até alguém
 * ligar): nada é criado. Depois, as contas são ligadas uma a uma e são independentes.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestAccountsDisabledE2ETest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("security.token.secret", () -> "test-secret-de-pelo-menos-32-bytes-aqui");
  }

  @Autowired TestRestTemplate http;
  @Autowired UserRepository users;
  @Autowired RoleRepository roles;
  @Autowired PermissionRepository permissions;
  @Autowired SectorRepository sectors;
  @Autowired SectorAllocationRepository allocations;
  @Autowired InventoryRepository inventories;
  @Autowired PasswordEncoder passwordEncoder;

  private TestAccountsInitializer initializer(String viewer, String operator, String manager) {
    return new TestAccountsInitializer(
        users,
        roles,
        permissions,
        sectors,
        allocations,
        inventories,
        passwordEncoder,
        viewer,
        operator,
        manager);
  }

  private HttpStatus login(String username, String password) {
    return HttpStatus.valueOf(
        http.postForEntity(
                "/auth/login", Map.of("username", username, "password", password), String.class)
            .getStatusCode()
            .value());
  }

  @Test
  @Order(1)
  void withoutAnyPasswordNothingIsCreated() {
    assertThat(users.findByEmail(TestAccountsInitializer.VIEWER_LOGIN)).isEmpty();
    assertThat(users.findByEmail(TestAccountsInitializer.OPERATOR_LOGIN)).isEmpty();
    assertThat(users.findByEmail(TestAccountsInitializer.MANAGER_LOGIN)).isEmpty();
    assertThat(sectors.findByName(TestAccountsInitializer.SECTOR_NAME)).isEmpty();
    assertThat(roles.findByName("Teste · Gestor")).isEmpty();
    assertThat(login(TestAccountsInitializer.MANAGER_LOGIN, "qualquer-senha-12345"))
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @Order(2)
  void accountsAreIndependentAndShareOneTestSector() {
    // Só o gestor ligado: só ele, o setor, o cargo dele e o inventário de exemplo existem.
    initializer("", "", "senha-gestor-teste-1").run(null);
    assertThat(login(TestAccountsInitializer.MANAGER_LOGIN, "senha-gestor-teste-1"))
        .isEqualTo(HttpStatus.OK);
    assertThat(users.findByEmail(TestAccountsInitializer.VIEWER_LOGIN)).isEmpty();
    assertThat(users.findByEmail(TestAccountsInitializer.OPERATOR_LOGIN)).isEmpty();
    assertThat(roles.findByName("Teste · Gestor")).isPresent();
    assertThat(roles.findByName("Teste · Consulta")).isEmpty();
    var sector = sectors.findByName(TestAccountsInitializer.SECTOR_NAME).orElseThrow();
    assertThat(inventories.findBySector_Id(sector.id())).hasSize(1);
    assertThat(inventories.findBySector_Id(sector.id()).get(0).User().getEmail())
        .isEqualTo(TestAccountsInitializer.MANAGER_LOGIN);

    // Ligar o operador depois entra no MESMO setor, sem criar outro setor nem outro inventário.
    initializer("", "senha-operador-teste-1", "senha-gestor-teste-1").run(null);
    assertThat(login(TestAccountsInitializer.OPERATOR_LOGIN, "senha-operador-teste-1"))
        .isEqualTo(HttpStatus.OK);
    User operator = users.findByEmail(TestAccountsInitializer.OPERATOR_LOGIN).orElseThrow();
    assertThat(allocations.findByUser_Id(operator.getId()))
        .singleElement()
        .satisfies(allocation -> assertThat(allocation.sector().id()).isEqualTo(sector.id()));
    assertThat(
            sectors.findAll().stream()
                .filter(s -> s.name().equals(TestAccountsInitializer.SECTOR_NAME)))
        .hasSize(1);
    assertThat(inventories.findBySector_Id(sector.id())).hasSize(1);
    assertThat(users.findByEmail(TestAccountsInitializer.VIEWER_LOGIN)).isEmpty();
  }

  @Test
  @Order(3)
  void aRegularUserWithTheSameIdentifierIsNeverTouched() {
    User regular = new User();
    regular.setName("Pessoa comum");
    regular.setEmail(TestAccountsInitializer.VIEWER_LOGIN);
    regular.setGoogleId("google-" + TestAccountsInitializer.VIEWER_LOGIN);
    regular = users.save(regular);

    initializer("senha-consulta-teste-1", "", "").run(null);

    User untouched = users.findById(regular.getId()).orElseThrow();
    assertThat(untouched.isSystemUser()).isFalse();
    assertThat(untouched.getPasswordHash()).isNull();
    assertThat(allocations.findByUser_Id(untouched.getId())).isEmpty();
    assertThat(login(TestAccountsInitializer.VIEWER_LOGIN, "senha-consulta-teste-1"))
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
