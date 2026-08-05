package clp.inventory.dto;

// Importa a classe Item do pacote clp.inventory.model.
// Isso indica que o ItemDto será usado para converter um objeto Item em um formato de transferência de dados.
import clp.inventory.model.Item;

// Importa classes relacionadas a manipulação de números decimais e formatação de números.
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;


// Define um record Java chamado 'ItemDto'.
// Records são classes concisas e imutáveis (a partir do Java 16), ideais para Data Transfer Objects (DTOs),
// pois o compilador gera automaticamente construtores, getters, equals(), hashCode() e toString().
public record ItemDto(
        // Componente 'id' do tipo long: o identificador único do item.
        long id,
        // Componente 'code' do tipo String: o código do item.
        String code,
        // Componente 'description' do tipo String: a descrição do item.
        String description,
        // Componente 'responsible' do tipo String: o responsável pelo item.
        String responsible,
        // Componente 'price' do tipo String: o preço do item, formatado como uma string de moeda.
        String price,
        // Componente 'locale' do tipo String: o local onde o item está.
        String locale,
        // Componente 'isValid' do tipo boolean: indica se o item é válido.
        boolean isValid,
        // Componente 'observations' do tipo List<ObservationDto>: uma lista de DTOs de observações associadas ao item.
        List<ObservationDto> observations
) {

    // Método estático de fábrica 'from'.
    // Este é um padrão comum para converter uma entidade de domínio (Item) para o DTO (ItemDto).
    // Ele encapsula a lógica de transformação dos dados.
    public static ItemDto from(Item item) {
        // Obtém uma instância de NumberFormat configurada para formatar valores monetários no Brasil (português/BR).
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        String formattedPrice; // Variável para armazenar o preço formatado.

        try {
            // Converte o preço do item (que é um long, presumivelmente em centavos ou menor unidade monetária)
            // para BigDecimal.
            // Divide o valor por 100 (para converter de centavos para reais, por exemplo),
            // com 2 casas decimais e arredondamento HALF_UP (arredonda para o vizinho mais próximo,
            // e se equidistante, arredonda para cima).
            // Em seguida, formata esse BigDecimal como uma string de moeda.
            formattedPrice = currencyFormat.format(
                    new BigDecimal(item.price()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
            );
        } catch (NumberFormatException e) {
            // Caso ocorra um erro na conversão ou formatação do número (improvável se item.price() for sempre um long válido),
            // define o preço formatado como "R$ 0,00" para evitar falhas.
            formattedPrice = "R$ 0,00";
        }

        // Converte a lista de observações do item (do modelo) para uma lista de ObservationDto.
        // Usa um stream para mapear cada Observation para um ObservationDto usando o método de fábrica 'from'
        // da própria classe ObservationDto, e coleta os resultados em uma lista.
        List<ObservationDto> observationsDto = item.observations().stream()
                .map(ObservationDto::from) // ou outro método para criar o DTO
                .toList(); // Coleta os elementos do stream em uma nova lista (disponível a partir do Java 16).

        // Retorna uma nova instância de ItemDto, preenchendo seus componentes
        // com os dados do objeto Item fornecido e os valores formatados/convertidos.
        return new ItemDto(
                item.id(),          // Mapeia o ID do item.
                item.code(),        // Mapeia o código do item.
                item.description(), // Mapeia a descrição do item.
                item.responsible(), // Mapeia o responsável pelo item.
                formattedPrice,     // Mapeia o preço formatado.
                item.locale(),      // Mapeia o local do item.
                item.isValid(),     // Mapeia o status de validade do item.
                observationsDto     // Mapeia a lista de DTOs de observações.
        );
    }
}