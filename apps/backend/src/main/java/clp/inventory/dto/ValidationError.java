package clp.inventory.dto;

// Importa a classe List do pacote java.util para usar coleções de strings.
import java.util.List;

// Define um record Java chamado 'ValidationError'.
// Records são uma funcionalidade do Java (a partir do Java 16) que oferece uma forma concisa de declarar
// classes para transportar dados imutáveis. Eles são ideais para DTOs (Data Transfer Objects),
// especialmente para representar estruturas de dados simples como esta, que contém informações sobre erros de validação.
// O compilador gera automaticamente o construtor canônico, métodos de acesso (getters),
// e as implementações de equals(), hashCode() e toString(), reduzindo o código boilerplate.
public record ValidationError(
        // Declara o componente 'code' do tipo String.
        // Este campo provavelmente armazenará o código de identificação do item que causou o erro.
        // Isso é útil para referenciar qual item específico falhou na validação.
        String code,
        // Declara o componente 'line' do tipo int.
        // Este campo indica o número da linha no arquivo (por exemplo, uma planilha Excel)
        // onde o erro foi encontrado. Isso ajuda a identificar rapidamente a localização do problema.
        int line,
        // Declara o componente 'errors' do tipo List<String>.
        // Este campo conterá uma lista de mensagens de erro detalhadas, explicando
        // os motivos específicos pelos quais a validação falhou para o item em questão.
        List<String> errors
) {
    // Como um record, não há necessidade de definir explicitamente:
    // - Um construtor com os parâmetros 'code', 'line' e 'errors'.
    // - Métodos de acesso como `code()`, `line()`, `errors()`.
    // - As implementações de `equals()`, `hashCode()` ou `toString()`.
    // Todas essas funcionalidades são geradas automaticamente pelo compilador,
    // tornando o ValidationError um DTO eficiente e imutável para relatar erros de validação.
}