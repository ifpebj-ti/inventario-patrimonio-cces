package clp.inventory.dto;

public record EmailRequestDto(
        String email,
        String subject,
        String message
) {
}
