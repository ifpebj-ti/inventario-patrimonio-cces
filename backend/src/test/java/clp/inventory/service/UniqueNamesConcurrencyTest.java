package clp.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import clp.inventory.dto.InventoryDto;
import clp.inventory.model.Inventory;
import clp.inventory.model.Room;
import clp.inventory.model.Sector;
import clp.inventory.model.User;
import clp.inventory.repository.InventoryRepository;
import clp.inventory.repository.RoomRepository;
import clp.inventory.repository.SectorRepository;
import clp.inventory.testsupport.TestAccess;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * FIN-04: nomes únicos não podem depender só da conferência (exists + save). O banco é a palavra
 * final, e a corrida vira erro de validação ou o mesmo resultado, nunca 500 nem duplicata.
 */
@Testcontainers
@SpringBootTest(
    properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.mail.password=test"})
class UniqueNamesConcurrencyTest {
  private static final int THREADS = 8;

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("security.token.secret", () -> "unique-names-test-secret-32-bytes-long");
  }

  @Autowired InventoryService inventoryService;
  @Autowired RoomService roomService;
  @Autowired SectorService sectorService;
  @Autowired InventoryRepository inventories;
  @Autowired RoomRepository rooms;
  @Autowired SectorRepository sectors;
  @Autowired TestAccess access;

  private User admin() {
    return access.user("unique-" + System.nanoTime() + "@ifpe.edu.br", TestAccess.ADMIN);
  }

  private InventoryDto dto(String name, Sector sector) {
    return new InventoryDto(
        0,
        name,
        "",
        null,
        null,
        LocalDate.now().plusDays(5).toString(),
        null,
        null,
        sector.id(),
        null,
        0);
  }

  /** Dispara as chamadas juntas (barreira) e devolve o resultado ou a exceção de cada uma. */
  private <T> List<Object> race(Callable<T> call) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    try {
      CyclicBarrier barrier = new CyclicBarrier(THREADS);
      List<Future<Object>> futures = new ArrayList<>();
      for (int i = 0; i < THREADS; i++) {
        futures.add(
            pool.submit(
                () -> {
                  barrier.await();
                  try {
                    return call.call();
                  } catch (Exception e) {
                    return e;
                  }
                }));
      }
      List<Object> results = new ArrayList<>();
      for (Future<Object> future : futures) {
        results.add(future.get());
      }
      return results;
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void concurrentCreateWithSameNameKeepsOneInventoryAndRejectsTheRestAsValidation()
      throws Exception {
    User admin = admin();
    Sector sector = sectors.save(new Sector("Race inv " + System.nanoTime(), null));
    String name = "Race " + System.nanoTime();

    List<Object> results =
        race(() -> inventoryService.createInventory(dto(name, sector), admin.getId()));

    assertThat(results.stream().filter(Inventory.class::isInstance)).hasSize(1);
    assertThat(results.stream().filter(r -> !(r instanceof Inventory)))
        .allSatisfy(r -> assertThat(r).isInstanceOf(IllegalArgumentException.class));
    assertThat(inventories.findAll().stream().filter(i -> i.name().equals(name))).hasSize(1);
  }

  @Test
  void databaseRefusesTwoInventoriesWithSameNameAndOwner() {
    User admin = admin();
    Sector sector = sectors.save(new Sector("Index inv " + System.nanoTime(), null));
    String name = "Index " + System.nanoTime();
    inventories.saveAndFlush(new Inventory(name, "", LocalDate.now(), admin, sector));

    assertThatThrownBy(
            () -> inventories.saveAndFlush(new Inventory(name, "", LocalDate.now(), admin, sector)))
        .isInstanceOf(DataIntegrityViolationException.class);
    // Outro responsável pode usar o mesmo nome.
    inventories.saveAndFlush(new Inventory(name, "", LocalDate.now(), admin(), sector));
  }

  @Test
  void renamingToAnExistingNameIsRejected() {
    User admin = admin();
    Sector sector = sectors.save(new Sector("Rename inv " + System.nanoTime(), null));
    String taken = "Taken " + System.nanoTime();
    inventoryService.createInventory(dto(taken, sector), admin.getId());
    Inventory other =
        inventoryService.createInventory(dto("Other " + System.nanoTime(), sector), admin.getId());

    assertThatThrownBy(
            () -> inventoryService.updateInventory(other.id(), dto(taken, sector), admin.getId()))
        .isInstanceOf(IllegalArgumentException.class);
    // Salvar com o próprio nome não conta como duplicata.
    inventoryService.updateInventory(other.id(), dto(other.name(), sector), admin.getId());
  }

  @Test
  void concurrentFindOrCreateOfSameRoomReturnsTheSameRoom() throws Exception {
    Sector sector = sectors.save(new Sector("Race room " + System.nanoTime(), null));
    String name = "Sala " + System.nanoTime();

    List<Object> results = race(() -> roomService.findOrCreate(sector, name));

    assertThat(results).allSatisfy(r -> assertThat(r).isInstanceOf(Room.class));
    assertThat(results.stream().map(r -> ((Room) r).id()).distinct()).hasSize(1);
    assertThat(rooms.findBySector_IdOrderByName(sector.id())).hasSize(1);
  }

  @Test
  void concurrentCreateOfSameRoomKeepsOneAndRejectsTheRestAsValidation() throws Exception {
    User admin = admin();
    Sector sector = sectors.save(new Sector("Race room create " + System.nanoTime(), null));
    String name = "Sala " + System.nanoTime();

    List<Object> results = race(() -> roomService.create(sector.id(), name, admin.getId()));

    assertThat(results.stream().filter(r -> !(r instanceof Exception))).hasSize(1);
    assertThat(results.stream().filter(Exception.class::isInstance))
        .allSatisfy(
            r ->
                assertThat(r)
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class));
    assertThat(rooms.findBySector_IdOrderByName(sector.id())).hasSize(1);
  }

  @Test
  void concurrentCreateOfSameSectorKeepsOneAndRejectsTheRestAsValidation() throws Exception {
    User admin = admin();
    String name = "Setor race " + System.nanoTime();

    List<Object> results =
        race(
            () ->
                sectorService.createSector(
                    new clp.inventory.dto.SectorDto(0, name, null, true, null, null, false),
                    admin.getId()));

    assertThat(results.stream().filter(Sector.class::isInstance)).hasSize(1);
    assertThat(results.stream().filter(r -> !(r instanceof Sector)))
        .allSatisfy(r -> assertThat(r).isInstanceOf(IllegalArgumentException.class));
  }
}
