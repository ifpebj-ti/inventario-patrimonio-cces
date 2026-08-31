package clp.inventory.dto;

import clp.inventory.model.Observation;

public record ObservationDto(
        long id,
        String content
) {

    public static ObservationDto from(Observation observation) {
        return new ObservationDto(
                observation.id(),
                observation.content()
        );
    }
}
