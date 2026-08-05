package clp.inventory.dto;

// Importa a classe Observation do pacote clp.inventory.model.
// Isso indica que este DTO será usado para converter um objeto Observation do modelo em um formato de transferência de dados.
import clp.inventory.model.Observation;

// Define um record Java chamado 'ObservationDto'.
// Records são classes concisas e imutáveis introduzidas no Java 16, ideais para Data Transfer Objects (DTOs).
// Eles reduzem a quantidade de código boilerplate (construtores, getters, equals, hashCode, toString)
// pois o compilador os gera automaticamente com base nos componentes declarados.
public record ObservationDto(
        // Componente 'id' do tipo long: representa o identificador único da observação.
        long id,
        // Componente 'content' do tipo String: representa o conteúdo textual da observação.
        String content
) {

    // Método estático de fábrica 'from'.
    // Este é um padrão comum em DTOs para converter uma entidade de domínio (Observation)
    // para o formato DTO (ObservationDto).
    // Isso encapsula a lógica de conversão e promove a reutilização do código.
    public static ObservationDto from(Observation observation) {
        // Retorna uma nova instância de ObservationDto, preenchendo seus componentes
        // com os valores correspondentes do objeto Observation fornecido.
        return new ObservationDto(
                observation.id(),      // Mapeia o ID da observação.
                observation.content()  // Mapeia o conteúdo da observação.
        );
    }
}