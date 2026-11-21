package clp.inventory.repository;

import clp.inventory.model.Item;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {

  boolean existsByInventory_IdAndCode(Long inventoryId, String code);

  Page<Item> findByInventory_Id(Long inventoryId, Pageable pageable);

  // Contagens usadas para o status do inventário e os indicadores do dashboard.
  long countByInventory_Id(Long inventoryId);

  long countByInventory_IdAndValidationStatus(Long inventoryId, Item.ValidationStatus status);

  // Itens ligados a uma sala (cadastrada ou encontrada): impedem excluir a sala.
  long countByRoom_Id(long roomId);

  long countByFoundRoom_Id(long roomId);

  // Histórico = qualquer linha em im_item_audit (validou, desvalidou ou marcou como não
  // localizado). A FK da auditoria impede excluir esses itens, e a regra de negócio é a mesma:
  // quem já sofreu uma ação não é mais excluído. O status atual não serve de critério: um item
  // desvalidado volta a PENDENTE e continua com histórico.
  @Query(
      "select count(i) from Item i where i.inventory.id = :inventoryId"
          + " and not exists (select 1 from ItemAudit a where a.item = i)")
  long countWithoutHistory(@Param("inventoryId") long inventoryId);

  @Query("select distinct a.item.id from ItemAudit a where a.item.inventory.id = :inventoryId")
  List<Long> findIdsWithHistory(@Param("inventoryId") long inventoryId);

  @Query("select count(a) > 0 from ItemAudit a where a.item.id = :itemId")
  boolean hasHistory(@Param("itemId") long itemId);

  @Query("select count(a) > 0 from ItemAudit a where a.item.inventory.id = :inventoryId")
  boolean anyHasHistory(@Param("inventoryId") long inventoryId);

  @Query("select i.code from Item i where i.inventory.id = :inventoryId")
  List<String> findCodesByInventoryId(@Param("inventoryId") long inventoryId);
}
