package clp.inventory.dto;

/** Corpo de PATCH /item/{id}/validate: a sala em que o item foi encontrado. */
public record ValidateItemRequest(Long roomId) {}
