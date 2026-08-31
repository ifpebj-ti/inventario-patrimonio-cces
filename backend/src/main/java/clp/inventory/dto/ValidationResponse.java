package clp.inventory.dto;

import java.util.List;

public record ValidationResponse(
        boolean hasErrors,
        List<ValidationError> errors
) {
}
