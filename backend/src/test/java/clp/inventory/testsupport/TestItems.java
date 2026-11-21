package clp.inventory.testsupport;

import clp.inventory.model.Item;
import clp.inventory.repository.ItemRepository;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Atalhos de teste para a situação dos itens. Finalizar um inventário exige que nenhum item esteja
 * PENDENTE, então os testes que só querem um inventário finalizado conferem os itens antes.
 */
@Component
public class TestItems {

  private final ItemRepository items;

  public TestItems(ItemRepository items) {
    this.items = items;
  }

  /** Marca todos os itens do inventário como VALIDADO, direto no banco, sem auditoria. */
  public void validateAll(long inventoryId) {
    setAll(inventoryId, Item.ValidationStatus.VALIDADO);
  }

  /** Marca todos os itens do inventário como NAO_LOCALIZADO, direto no banco, sem auditoria. */
  public void markAllNotFound(long inventoryId) {
    setAll(inventoryId, Item.ValidationStatus.NAO_LOCALIZADO);
  }

  private void setAll(long inventoryId, Item.ValidationStatus status) {
    for (Item item : items.findByInventory_Id(inventoryId, Pageable.unpaged())) {
      item.setValidationStatus(status);
      item.setValidatedAt(LocalDateTime.now());
      items.save(item);
    }
  }
}
