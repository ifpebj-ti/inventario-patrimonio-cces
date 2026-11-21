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
 * A migration 14.0 troca o booleano im_item.is_valid pela situação do item (validation_status:
 * PENDENTE, VALIDADO ou NAO_LOCALIZADO). O schema é levado até a 8.0 (quando is_valid ainda é a
 * fonte da verdade), recebe itens validados e não validados e só então o master completo é
 * aplicado.
 */
@Testcontainers
class MigrationValidationStatusTest {

  private static final String UNTIL_8_0 = "db/changelog/changelog-until-8.0.xml";
  private static final String MASTER = "db/changelog/db.changelog-master.xml";

  @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @BeforeEach
  void resetSchema() throws Exception {
    execute("DROP SCHEMA public CASCADE");
    execute("CREATE SCHEMA public");
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

  private void seed() throws SQLException {
    execute(
        "INSERT INTO im_sector (id, name, active, created_at, updated_at)"
            + " VALUES (1, 'Setor A', true, now(), now())");
    execute(
        "INSERT INTO im_user (id, name, email, google_id, created_at, updated_at, is_system_user,"
            + " active) VALUES (1, 'Dono', 'dono@ifpe.edu.br', 'sub-1', now(), now(), false, true)");
    execute(
        "INSERT INTO im_inventory (id, name, created_at, updated_at, id_user, id_sector) VALUES"
            + " (1, 'Inv A', now(), now(), 1, 1)");
    for (int id = 1; id <= 5; id++) {
      // Itens 1, 2 e 3 validados; 4 e 5 ainda não.
      execute(
          "INSERT INTO im_item (id, code, qr_code, is_valid, id_inventory) VALUES ("
              + id
              + ", 'COD-"
              + id
              + "', 'qr-"
              + id
              + "', "
              + (id <= 3)
              + ", 1)");
    }
  }

  @Test
  void validatedItemsBecomeValidadoAndTheRestPendente() throws Exception {
    migrate(UNTIL_8_0);
    seed();

    migrate(MASTER);

    assertThat(count("SELECT count(*) FROM im_item WHERE validation_status = 'VALIDADO'"))
        .isEqualTo(3);
    assertThat(count("SELECT count(*) FROM im_item WHERE validation_status = 'PENDENTE'"))
        .isEqualTo(2);
    assertThat(
            count("SELECT count(*) FROM im_item WHERE id <= 3 AND validation_status = 'VALIDADO'"))
        .isEqualTo(3);
  }

  @Test
  void isValidColumnIsGoneSoThereIsOneSourceOfTruth() throws Exception {
    migrate(UNTIL_8_0);
    seed();

    migrate(MASTER);

    assertThat(
            count(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'im_item'"
                    + " AND column_name = 'is_valid'"))
        .isZero();
  }

  @Test
  void newItemsDefaultToPendenteAndTheStatusIsConstrained() throws Exception {
    migrate(MASTER);
    execute(
        "INSERT INTO im_sector (id, name, active, created_at, updated_at)"
            + " VALUES (1, 'Setor A', true, now(), now())");
    execute(
        "INSERT INTO im_user (id, name, email, google_id, created_at, updated_at, is_system_user,"
            + " active) VALUES (1, 'Dono', 'dono@ifpe.edu.br', 'sub-1', now(), now(), false, true)");
    execute(
        "INSERT INTO im_inventory (id, name, created_at, updated_at, id_user, id_sector) VALUES"
            + " (1, 'Inv A', now(), now(), 1, 1)");

    execute("INSERT INTO im_item (id, code, qr_code, id_inventory) VALUES (1, 'A', 'qr', 1)");
    assertThat(count("SELECT count(*) FROM im_item WHERE validation_status = 'PENDENTE'"))
        .isEqualTo(1);
    execute(
        "INSERT INTO im_item (id, code, qr_code, id_inventory, validation_status)"
            + " VALUES (2, 'B', 'qr2', 1, 'NAO_LOCALIZADO')");

    assertThatThrownBy(
            () ->
                execute(
                    "INSERT INTO im_item (id, code, qr_code, id_inventory, validation_status)"
                        + " VALUES (3, 'C', 'qr3', 1, 'QUALQUER')"))
        .hasMessageContaining("ck_item_validation_status");
  }
}
