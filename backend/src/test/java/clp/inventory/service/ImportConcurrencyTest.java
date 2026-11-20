package clp.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import clp.inventory.model.Inventory;
import clp.inventory.model.Item;
import clp.inventory.model.Sector;
import clp.inventory.model.User;
import clp.inventory.repository.InventoryRepository;
import clp.inventory.repository.ItemRepository;
import clp.inventory.repository.RoomRepository;
import clp.inventory.repository.SectorRepository;
import clp.inventory.testsupport.TestAccess;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * FIN-05: a importação é tudo ou nada e uma importação por inventário. O lock serializa as
 * importações simultâneas, a transação desfaz tudo se algo falha e o índice único (inventário,
 * código) é a última barreira contra código repetido.
 */
@Testcontainers
@SpringBootTest(
    properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.mail.password=test"})
class ImportConcurrencyTest {
  private static final int THREADS = 8;

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("security.token.secret", () -> "import-concurrency-test-secret-32-bytes");
  }

  @Autowired InventoryService inventoryService;
  @Autowired InventoryRepository inventories;
  @Autowired ItemRepository items;
  @Autowired RoomRepository rooms;
  @Autowired SectorRepository sectors;
  @Autowired TestAccess access;

  private User admin() {
    return access.user("import-" + System.nanoTime() + "@ifpe.edu.br", TestAccess.ADMIN);
  }

  private Sector sector() {
    return sectors.save(new Sector("Import " + System.nanoTime(), null));
  }

  private Inventory inventory(User owner, Sector sector) {
    return inventories.save(
        new Inventory(
            "Import " + System.nanoTime(), "", LocalDate.now().plusDays(5), owner, sector));
  }

  private List<Item> sheet(String room, String... codes) {
    List<Item> list = new ArrayList<>();
    for (String code : codes) {
      list.add(new Item(code, "", "Mesa", 1000, room, "Maria"));
    }
    return list;
  }

  @Test
  void simultaneousImportsOfTheSameInventoryKeepOneAndRejectTheRestWithConflict() throws Exception {
    User admin = admin();
    Inventory inventory = inventory(admin, sector());
    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    try {
      CyclicBarrier barrier = new CyclicBarrier(THREADS);
      List<Future<Object>> futures = new ArrayList<>();
      for (int i = 0; i < THREADS; i++) {
        // Códigos diferentes por importação: o índice único não as barra, só o lock as serializa.
        String prefix = "A" + i + "-";
        futures.add(
            pool.submit(
                () -> {
                  barrier.await();
                  try {
                    return inventoryService.importItems(
                        inventory.id(),
                        sheet("Sala A", prefix + "1", prefix + "2", prefix + "3"),
                        admin.getId());
                  } catch (Exception e) {
                    return e;
                  }
                }));
      }
      List<Object> results = new ArrayList<>();
      for (Future<Object> future : futures) {
        results.add(future.get());
      }

      assertThat(results.stream().filter(InventoryService.ImportResult.class::isInstance))
          .hasSize(1);
      assertThat(results.stream().filter(Exception.class::isInstance))
          .hasSize(THREADS - 1)
          .allSatisfy(
              e -> {
                assertThat(e).isInstanceOf(ResponseStatusException.class);
                assertThat(((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
              });
      assertThat(items.countByInventory_Id(inventory.id())).isEqualTo(3);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void importThatFailsRollsBackItemsAndRoomsCreatedByIt() {
    User admin = admin();
    Sector sector = sector();
    Inventory inventory = inventory(admin, sector);
    // O código repetido só é recusado na gravação (o índice); a sala da planilha já foi criada.
    List<Item> sheet = sheet("Sala Nova", "B-1", "B-2", "B-2");

    assertThatThrownBy(() -> inventoryService.importItems(inventory.id(), sheet, admin.getId()))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

    assertThat(items.countByInventory_Id(inventory.id())).isZero();
    assertThat(rooms.findBySector_IdOrderByName(sector.id())).isEmpty();
    // Sem itens, uma nova tentativa com a planilha corrigida passa.
    assertThat(
            inventoryService
                .importItems(inventory.id(), sheet("Sala Nova", "B-1", "B-2"), admin.getId())
                .imported())
        .isEqualTo(2);
  }

  @Test
  void importIntoFinishedInventoryIsRejected() {
    User admin = admin();
    Inventory inventory = inventory(admin, sector());
    inventoryService.finishInventory(inventory.id(), admin.getId());

    assertThatThrownBy(
            () -> inventoryService.importItems(inventory.id(), sheet(null, "C-1"), admin.getId()))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    assertThat(items.countByInventory_Id(inventory.id())).isZero();
  }

  @Test
  void databaseRefusesRepeatedCodeInTheSameInventoryButAllowsItInAnother() {
    User admin = admin();
    Sector sector = sector();
    Inventory first = inventory(admin, sector);
    Inventory second = inventory(admin, sector);
    String code = "D-" + System.nanoTime();
    first.addItem(new Item(code, "", "Mesa", 1000, null, "Maria"));
    inventories.saveAndFlush(first);

    first.addItem(new Item(code, "", "Mesa", 1000, null, "Maria"));
    assertThatThrownBy(() -> inventories.saveAndFlush(first))
        .isInstanceOf(DataIntegrityViolationException.class);

    second.addItem(new Item(code, "", "Mesa", 1000, null, "Maria"));
    inventories.saveAndFlush(second);
    assertThat(items.countByInventory_Id(second.id())).isEqualTo(1);
  }
}
