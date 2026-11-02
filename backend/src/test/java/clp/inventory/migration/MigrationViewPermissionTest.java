package clp.inventory.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * A migration 8.0 cria a permissão de Consulta (VIEW_SECTOR) e a dá a todos os cargos que já
 * existem, para que quem lia o setor por estar alocado continue lendo. O schema é levado até a 7.0,
 * recebe cargos (inclusive um sem nenhuma permissão) e só então o master completo é aplicado.
 */
@Testcontainers
class MigrationViewPermissionTest {

  private static final String UNTIL_7_0 = "db/changelog/changelog-until-7.0.xml";
  private static final String MASTER = "db/changelog/db.changelog-master.xml";

  @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @BeforeEach
  void resetSchema() throws Exception {
    try (Connection connection = open();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP SCHEMA public CASCADE");
      statement.execute("CREATE SCHEMA public");
    }
  }

  private Connection open() throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  private void migrate(String changelog) throws Exception {
    try (Connection connection = open()) {
      Database database =
          DatabaseFactory.getInstance()
              .findCorrectDatabaseImplementation(new JdbcConnection(connection));
      try (Liquibase liquibase =
          new Liquibase(changelog, new ClassLoaderResourceAccessor(), database)) {
        liquibase.update(new Contexts(), new LabelExpression());
      }
    }
  }

  private void execute(String sql) throws SQLException {
    try (Connection connection = open();
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private long count(String sql) throws SQLException {
    try (Connection connection = open();
        Statement statement = connection.createStatement();
        var result = statement.executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }

  private long rolesWith(String permission) throws SQLException {
    return count(
        "SELECT count(DISTINCT rp.id_role) FROM im_role_permission rp"
            + " JOIN im_permission p ON p.id = rp.id_permission WHERE p.name = '"
            + permission
            + "'");
  }

  @Test
  void everyExistingRoleGetsTheViewPermissionAndKeepsItsOthers() throws Exception {
    migrate(UNTIL_7_0);
    // Um cargo criado por um administrador, sem nenhuma permissão: antes lia o setor só por estar
    // alocado.
    execute(
        "INSERT INTO im_role (id, name, description, created_at, updated_at)"
            + " VALUES (nextval('im_role_id'), 'Sem nada', NULL, now(), now())");
    long rolesBefore = count("SELECT count(*) FROM im_role");
    long linksBefore = count("SELECT count(*) FROM im_role_permission");
    long manageSectorBefore = rolesWith("MANAGE_SECTOR");
    long manageItemBefore = rolesWith("MANAGE_ITEM");
    assertThat(rolesWith("VIEW_SECTOR")).isZero();

    migrate(MASTER);

    assertThat(count("SELECT count(*) FROM im_permission WHERE name = 'VIEW_SECTOR'")).isEqualTo(1);
    assertThat(count("SELECT count(*) FROM im_permission")).isEqualTo(3);
    // Todos os cargos que existiam (os de origem e o sem permissão) ganham a Consulta.
    assertThat(count("SELECT count(*) FROM im_role")).isEqualTo(rolesBefore);
    assertThat(rolesWith("VIEW_SECTOR")).isEqualTo(rolesBefore);
    assertThat(count("SELECT count(*) FROM im_role_permission"))
        .isEqualTo(linksBefore + rolesBefore);
    // As permissões que já tinham seguem as mesmas.
    assertThat(rolesWith("MANAGE_SECTOR")).isEqualTo(manageSectorBefore);
    assertThat(rolesWith("MANAGE_ITEM")).isEqualTo(manageItemBefore);
    assertThat(
            count(
                "SELECT count(*) FROM im_role r WHERE r.name = 'Consulta' AND"
                    + " (SELECT count(*) FROM im_role_permission rp WHERE rp.id_role = r.id) = 1"))
        .isEqualTo(1);
  }

  @Test
  void aFreshDatabaseAlsoGetsTheThreePermissions() throws Exception {
    migrate(MASTER);

    assertThat(count("SELECT count(*) FROM im_permission")).isEqualTo(3);
    assertThat(rolesWith("VIEW_SECTOR")).isEqualTo(count("SELECT count(*) FROM im_role"));
  }
}
