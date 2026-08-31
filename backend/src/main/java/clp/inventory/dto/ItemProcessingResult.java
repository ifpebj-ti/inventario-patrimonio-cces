package clp.inventory.dto;

public record ItemProcessingResult(
        String code,
        int line,
        boolean success,
        String message
) {
}
