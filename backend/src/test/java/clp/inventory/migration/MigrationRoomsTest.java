package clp.inventory.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
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
 * A migration 9.0 transforma o texto livre de sala dos itens (im_item.locale) em salas por setor. O
 * schema é levado até a 8.0, recebe itens com grafias variadas em dois setores e só então o master
 * completo é aplicado.
 */
@Testcontainers
class MigrationRoomsTest {

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

  private List<String> strings(String sql) throws SQLException {
    List<String> values = new ArrayList<>();
    try (Connection connection = open();
        Statement statement = connection.createStatement();
        var result = statement.executeQuery(sql)) {
      while (result.next()) {
        values.add(result.getString(1));
      }
    }
    return values;
  }

  private void insertItem(long id, long inventoryId, String locale) throws SQLException {
    execute(
        "INSERT INTO im_item (id, code, qr_code, is_valid, id_inventory, locale) VALUES ("
            + id
            + ", 'COD-"
            + id
            + "', 'qr-"
            + id
            + "', false, "
            + inventoryId
            + ", "
            + (locale == null ? "NULL" : "'" + locale + "'")
            + ")");
  }

  private void seed() throws SQLException {
    execute(
        "INSERT INTO im_sector (id, name, active, created_at, updated_at)"
            + " VALUES (1, 'Setor A', true, now(), now()), (2, 'Setor B', true, now(), now())");
    execute(
        "INSERT INTO im_user (id, name, email, google_id, created_at, updated_at, is_system_user,"
            + " active) VALUES (1, 'Dono', 'dono@ifpe.edu.br', 'sub-1', now(), now(), false, true)");
    execute(
        "INSERT INTO im_inventory (id, name, created_at, updated_at, id_user, id_sector) VALUES"
            + " (1, 'Inv A', now(), now(), 1, 1), (2, 'Inv B', now(), now(), 1, 2),"
            + " (3, 'Inv sem setor', now(), now(), 1, NULL)");
    // Setor A: "Sala 105" escrita de quatro jeitos, "Laboratório 1" de dois, e itens sem sala.
    insertItem(1, 1, "Sala 105");
    insertItem(2, 1, "Sala 105");
    insertItem(3, 1, "sala  105");
    insertItem(4, 1, " SALA 105 ");
    insertItem(5, 1, "Laboratório 1");
    insertItem(6, 1, "LABORATORIO 1");
    insertItem(7, 1, "   ");
    insertItem(8, 1, null);
    // Setor B: a mesma "Sala 105" é outra sala, de outro setor.
    insertItem(9, 2, "Sala 105");
    // Inventário sem setor: não há onde criar a sala.
    insertItem(10, 3, "Sala X");
  }

  @Test
  void localeTextsBecomeNormalizedRoomsPerSector_andItemsPointToThem() throws Exception {
    migrate(UNTIL_8_0);
    seed();

    migrate(MASTER);

    assertThat(count("SELECT count(*) FROM im_room WHERE id_sector = 1")).isEqualTo(2);
    assertThat(count("SELECT count(*) FROM im_room WHERE id_sector = 2")).isEqualTo(1);
    assertThat(count("SELECT count(*) FROM im_room")).isEqualTo(3);
    // O nome exibido é a grafia mais frequente, já com os espaços arrumados.
    assertThat(
            strings(
                "SELECT name FROM im_room WHERE id_sector = 1 AND normalized_name = 'sala 105'"))
        .containsExactly("Sala 105");
    assertThat(
            strings(
                "SELECT normalized_name FROM im_room WHERE id_sector = 1 ORDER BY normalized_name"))
        .containsExactly("laboratorio 1", "sala 105");

    // As quatro grafias de "Sala 105" do setor A caem na mesma sala; B tem a sua.
    assertThat(
            count(
                "SELECT count(DISTINCT id_room) FROM im_item WHERE id IN (1, 2, 3, 4) AND id_room"
                    + " IS NOT NULL"))
        .isEqualTo(1);
    assertThat(count("SELECT count(*) FROM im_item WHERE id IN (1, 2, 3, 4) AND id_room IS NULL"))
        .isZero();
    assertThat(
            count(
                "SELECT count(DISTINCT id_room) FROM im_item WHERE id IN (5, 6) AND id_room IS NOT"
                    + " NULL"))
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM im_item i JOIN im_room r ON r.id = i.id_room WHERE i.id = 9"
                    + " AND r.id_sector = 2"))
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM im_item i JOIN im_room r ON r.id = i.id_room"
                    + " JOIN im_inventory inv ON inv.id = i.id_inventory"
                    + " WHERE r.id_sector != inv.id_sector"))
        .isZero();
  }

  @Test
  void blankLocaleAndInventoriesWithoutSectorGetNoRoom_andLocaleColumnIsKept() throws Exception {
    migrate(UNTIL_8_0);
    seed();

    migrate(MASTER);

    assertThat(count("SELECT count(*) FROM im_item WHERE id IN (7, 8, 10) AND id_room IS NULL"))
        .isEqualTo(3);
    assertThat(strings("SELECT locale FROM im_item WHERE id = 3")).containsExactly("sala  105");
    assertThat(strings("SELECT locale FROM im_item WHERE id = 10")).containsExactly("Sala X");
    assertThat(count("SELECT count(*) FROM im_item")).isEqualTo(10);
    assertThat(count("SELECT count(*) FROM im_item WHERE id_found_room IS NOT NULL")).isZero();
  }

  @Test
  void sameRoomNameInTwoSectorsIsAllowed_butNotTwiceInTheSameSector() throws Exception {
    migrate(MASTER);
    execute(
        "INSERT INTO im_sector (id, name, active, created_at, updated_at)"
            + " VALUES (1, 'Setor A', true, now(), now()), (2, 'Setor B', true, now(), now())");
    execute(
        "INSERT INTO im_room (id, id_sector, name, normalized_name) VALUES"
            + " (nextval('im_room_id'), 1, 'Sala 1', 'sala 1'),"
            + " (nextval('im_room_id'), 2, 'Sala 1', 'sala 1')");

    org.junit.jupiter.api.Assertions.assertThrows(
        SQLException.class,
        () ->
            execute(
                "INSERT INTO im_room (id, id_sector, name, normalized_name) VALUES"
                    + " (nextval('im_room_id'), 1, 'SALA 1', 'sala 1')"));
  }

  @Test
  void aFreshDatabaseGetsTheRoomStructure() throws Exception {
    migrate(MASTER);

    assertThat(count("SELECT count(*) FROM im_room")).isZero();
    assertThat(
            count(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'im_item_audit'"
                    + " AND column_name IN ('id_found_room', 'id_expected_room', 'divergent')"))
        .isEqualTo(3);
  }
}
