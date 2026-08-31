package clp.inventory.dto;

import java.util.List;

public record ValidationError(
        String code,
        int line,
        List<String> errors
) {
}
