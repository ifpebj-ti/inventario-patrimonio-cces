package clp.inventory.service;

/**
 * O inventário ainda não pode ser finalizado: não tem itens ou tem itens PENDENTE. Vira 422 com o
 * motivo e a quantidade de pendentes no corpo (o 409 fica para "já finalizado").
 */
public class InventoryNotReadyException extends RuntimeException {

  private final long pendingItems;

  public InventoryNotReadyException(String message, long pendingItems) {
    super(message);
    this.pendingItems = pendingItems;
  }

  public long pendingItems() {
    return pendingItems;
  }
}
