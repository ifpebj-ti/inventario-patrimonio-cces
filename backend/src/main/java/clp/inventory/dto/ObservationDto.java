package clp.inventory.dto;

import clp.inventory.model.Observation;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Observação anexada a um item.")
public record ObservationDto(
    @Schema(
            description = "Id da observação (ignorado ao enviar a lista; é gerado pelo servidor).",
            example = "12")
        long id,
    @Schema(description = "Texto da observação.", example = "Tela com pequeno risco.")
        String content) {

  public static ObservationDto from(Observation observation) {
    return new ObservationDto(observation.id(), observation.content());
  }
}
