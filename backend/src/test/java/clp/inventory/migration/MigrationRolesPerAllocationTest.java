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
 * A migration 7.0 move o perfil do usuário (im_user.id_profile) para o cargo da alocação
 * (im_sector_allocation.id_role) e transforma o perfil de administrador na coluna is_administrator.
 * Aqui o schema é levado até a 6.0, recebe usuários antigos de cada tipo (com perfil, sem perfil,
 * administrador, conta de sistema) e só então o master completo é aplicado, como acontece num banco
 * que já existia antes desta mudança.
 */
@Testcontainers
class MigrationRolesPerAllocationTest {

  private static final String UNTIL_6_0 = "db/changelog/changelog-until-6.0.xml";
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

  private void insertUser(long id, String profile, boolean system) throws SQLException {
    execute(
        "INSERT INTO im_user (id, name, email, google_id, created_at, updated_at, is_system_user,"
            + " active, id_profile) VALUES ("
            + id
            + ", 'Usuario "
            + id
            + "', 'u"
            + id
            + "@ifpe.edu.br', "
            + (system ? "NULL" : "'sub-" + id + "'")
            + ", now(), now(), "
            + system
            + ", true, "
            + (profile == null
                ? "NULL"
                : "(SELECT id FROM im_profile WHERE name = '" + profile + "')")
            + ")");
  }

  private void allocate(long allocationId, long userId, long sectorId) throws SQLException {
    execute(
        "INSERT INTO im_sector_allocation (id, id_user, id_sector) VALUES ("
            + allocationId
            + ", "
            + userId
            + ", "
            + sectorId
            + ")");
  }

  private long rolesOf(long userId, long sectorId, String roleName) throws SQLException {
    return count(
        "SELECT count(*) FROM im_sector_allocation a JOIN im_role r ON r.id = a.id_role"
            + " WHERE a.id_user = "
            + userId
            + " AND a.id_sector = "
            + sectorId
            + " AND r.name = '"
            + roleName
            + "'");
  }

  @Test
  void movesEachUsersProfileToTheRoleOfEveryAllocation() throws Exception {
    migrate(UNTIL_6_0);
    execute(
        "INSERT INTO im_sector (id, name, active, created_at, updated_at)"
            + " VALUES (1, 'Setor A', true, now(), now()), (2, 'Setor B', true, now(), now())");
    insertUser(1, "GESTOR_SETOR", false); // gestora em dois setores
    insertUser(2, "CONSULTA", false);
    insertUser(3, "ADMIN_ORGANIZATION", false); // administrador com uma alocação
    insertUser(4, null, false); // sem perfil, mas alocada
    insertUser(5, "ADMIN_ORGANIZATION", true); // conta de sistema
    insertUser(6, "OPERADOR_CAMPO", false); // com perfil, sem alocação
    insertUser(7, null, false); // sem perfil e sem alocação
    allocate(1, 1, 1);
    allocate(2, 1, 2);
    allocate(3, 2, 1);
    allocate(4, 3, 1);
    allocate(5, 4, 1);

    migrate(MASTER);

    // Quem tinha o perfil de administrador vira administrador; ninguém mais.
    assertThat(count("SELECT count(*) FROM im_user WHERE is_administrator")).isEqualTo(2);
    assertThat(count("SELECT count(*) FROM im_user WHERE is_administrator AND id IN (3, 5)"))
        .isEqualTo(2);
    // Cada alocação recebe o cargo que era o perfil do usuário; sem perfil vira Consulta; a
    // alocação do administrador vira Gestor de Setor.
    assertThat(rolesOf(1, 1, "Gestor de Setor")).isEqualTo(1);
    assertThat(rolesOf(1, 2, "Gestor de Setor")).isEqualTo(1);
    assertThat(rolesOf(2, 1, "Consulta")).isEqualTo(1);
    assertThat(rolesOf(3, 1, "Gestor de Setor")).isEqualTo(1);
    assertThat(rolesOf(4, 1, "Consulta")).isEqualTo(1);
    assertThat(count("SELECT count(*) FROM im_sector_allocation")).isEqualTo(5);
    // Todos os usuários continuam existindo.
    assertThat(count("SELECT count(*) FROM im_user")).isEqualTo(7);
  }

  @Test
  void removesTheAdministratorProfileAndPermissionAndRenamesTheSeeds() throws Exception {
    migrate(UNTIL_6_0);
    migrate(MASTER);

    assertThat(count("SELECT count(*) FROM im_role")).isEqualTo(3);
    assertThat(count("SELECT count(*) FROM im_role WHERE name = 'ADMIN_ORGANIZATION'")).isZero();
    assertThat(count("SELECT count(*) FROM im_permission")).isEqualTo(2);
    assertThat(count("SELECT count(*) FROM im_permission WHERE name = 'ADMIN'")).isZero();
    assertThat(
            count(
                "SELECT count(*) FROM im_role WHERE name IN"
                    + " ('Gestor de Setor', 'Operador de Campo', 'Consulta')"))
        .isEqualTo(3);
    // As permissões dos cargos semente seguem as mesmas.
    assertThat(
            count(
                "SELECT count(*) FROM im_role_permission rp JOIN im_role r ON r.id = rp.id_role"
                    + " JOIN im_permission p ON p.id = rp.id_permission"
                    + " WHERE (r.name = 'Gestor de Setor' AND p.name = 'MANAGE_SECTOR')"
                    + " OR (r.name = 'Operador de Campo' AND p.name = 'MANAGE_ITEM')"))
        .isEqualTo(2);
    assertThat(count("SELECT count(*) FROM im_role_permission")).isEqualTo(2);
    // O perfil sai do usuário.
    assertThat(
            count(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'im_user'"
                    + " AND column_name = 'id_profile'"))
        .isZero();
  }

  @Test
  void allocationWithoutRoleIsRejectedAfterTheMigration() throws Exception {
    migrate(UNTIL_6_0);
    migrate(MASTER);
    execute(
        "INSERT INTO im_sector (id, name, active, created_at, updated_at)"
            + " VALUES (1, 'Setor A', true, now(), now())");
    insertUserWithoutProfile(1);

    assertThatThrownBy(() -> allocate(1, 1, 1)).isInstanceOf(SQLException.class);
  }

  private void insertUserWithoutProfile(long id) throws SQLException {
    execute(
        "INSERT INTO im_user (id, name, email, google_id, created_at, updated_at, is_system_user,"
            + " active) VALUES ("
            + id
            + ", 'Usuario "
            + id
            + "', 'u"
            + id
            + "@ifpe.edu.br', 'sub-"
            + id
            + "', now(), now(), false, true)");
  }
}
