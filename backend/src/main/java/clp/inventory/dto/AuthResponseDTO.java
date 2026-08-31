package clp.inventory.dto;

import clp.inventory.model.User;

public record AuthResponseDTO(
        String token,
        User user
) {
}
