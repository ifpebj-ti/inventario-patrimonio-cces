package clp.inventory.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * A migration 6.0 remove o pré-cadastro de usuário (coluna pending_google_login) e devolve o CHECK
 * ck_user_system_google à forma estrita. Aqui o schema é levado até a 5.0, recebe dados antigos
 * (inclusive pré-cadastros) e só então o master completo é aplicado, como acontece num banco que já
 * existia antes desta mudança.
 */
@Testcontainers
class MigrationRemovesPendingGoogleLoginTest {

  private static final String UNTIL_5_0 = "db/changelog/changelog-until-5.0.xml";
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

  private void insertUser(
      long id, String email, String googleId, boolean systemUser, boolean pending)
      throws SQLException {
    execute(
        "INSERT INTO im_user (id, name, email, google_id, created_at, updated_at, is_system_user,"
            + " pending_google_login) VALUES ("
            + id
            + ", 'Usuario "
            + id
            + "', '"
            + email
            + "', "
            + (googleId == null ? "NULL" : "'" + googleId + "'")
            + ", now(), now(), "
            + systemUser
            + ", "
            + pending
            + ")");
  }

  @Test
  void removesPendingUsersAndTheirAllocationsAndKeepsEveryoneElse() throws Exception {
    migrate(UNTIL_5_0);
    execute(
        "INSERT INTO im_sector (id, name, active, created_at, updated_at)"
            + " VALUES (1, 'Setor A', true, now(), now())");
    insertUser(1, "google@ifpe.edu.br", "sub-1", false, false);
    insertUser(2, "admin", null, true, false);
    insertUser(3, "pre-cadastrado@ifpe.edu.br", null, false, true);
    insertUser(4, "outro-pre-cadastro@ifpe.edu.br", null, false, true);
    execute("INSERT INTO im_sector_allocation (id, id_user, id_sector) VALUES (1, 1, 1)");
    execute("INSERT INTO im_sector_allocation (id, id_user, id_sector) VALUES (2, 3, 1)");
    execute("INSERT INTO im_sector_allocation (id, id_user, id_sector) VALUES (3, 4, 1)");

    migrate(MASTER);

    assertThat(count("SELECT count(*) FROM im_user")).isEqualTo(2);
    assertThat(count("SELECT count(*) FROM im_user WHERE id IN (1, 2)")).isEqualTo(2);
    assertThat(count("SELECT count(*) FROM im_sector_allocation")).isEqualTo(1);
    assertThat(count("SELECT count(*) FROM im_sector_allocation WHERE id_user = 1")).isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'im_user'"
                    + " AND column_name = 'pending_google_login'"))
        .isZero();
  }

  @Test
  void strictCheckRefusesOrdinaryUserWithoutGoogleIdAndSystemUserWithGoogleId() throws Exception {
    migrate(UNTIL_5_0);
    migrate(MASTER);

    assertThatThrownBy(
            () ->
                execute(
                    "INSERT INTO im_user (id, name, email, google_id, created_at, updated_at,"
                        + " is_system_user) VALUES (10, 'x', 'sem-google@ifpe.edu.br', NULL,"
                        + " now(), now(), false)"))
        .hasMessageContaining("ck_user_system_google");
    assertThatThrownBy(
            () ->
                execute(
                    "INSERT INTO im_user (id, name, email, google_id, created_at, updated_at,"
                        + " is_system_user) VALUES (11, 'x', 'sistema', 'sub', now(), now(),"
                        + " true)"))
        .hasMessageContaining("ck_user_system_google");
    // Os dois ramos válidos continuam aceitos.
    execute(
        "INSERT INTO im_user (id, name, email, google_id, created_at, updated_at, is_system_user)"
            + " VALUES (12, 'x', 'ok@ifpe.edu.br', 'sub-12', now(), now(), false)");
    execute(
        "INSERT INTO im_user (id, name, email, google_id, created_at, updated_at, is_system_user)"
            + " VALUES (13, 'x', 'admin', NULL, now(), now(), true)");
    assertThat(count("SELECT count(*) FROM im_user")).isEqualTo(2);
  }

  @Test
  void isHarmlessWhenThereAreNoPendingUsers() throws Exception {
    migrate(UNTIL_5_0);
    insertUser(1, "google@ifpe.edu.br", "sub-1", false, false);
    insertUser(2, "admin", null, true, false);

    migrate(MASTER);

    assertThat(count("SELECT count(*) FROM im_user")).isEqualTo(2);
  }
}
