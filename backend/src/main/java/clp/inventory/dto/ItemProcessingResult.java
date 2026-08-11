package clp.inventory.dto;

// Define um record Java chamado 'ItemProcessingResult'.
// Este record serve como um Data Transfer Object (DTO) para encapsular
// o resultado do processamento de um item individual, geralmente após uma tentativa de adição
// ou atualização em um sistema de inventário.
// Records são uma funcionalidade do Java (a partir do Java 16) que oferece uma forma concisa de declarar
// classes para transportar dados imutáveis, eliminando a necessidade de escrever manualmente
// construtores, métodos de acesso (getters), equals(), hashCode() e toString().
public record ItemProcessingResult(
        // Declara o componente 'code' do tipo String.
        // Este campo provavelmente armazenará o código de identificação do item que foi processado.
        String code,
        // Declara o componente 'line' do tipo int.
        // Este campo pode indicar o número da linha em um arquivo (como uma planilha Excel)
        // de onde o item foi lido, útil para depuração e feedback ao usuário.
        int line,
        // Declara o componente 'success' do tipo boolean.
        // Este campo booleano indica se o processamento do item foi bem-sucedido (true) ou falhou (false).
        boolean success,
        // Declara o componente 'message' do tipo String.
        // Este campo conterá uma mensagem descritiva sobre o resultado do processamento.
        // Em caso de sucesso, pode ser uma mensagem confirmatória; em caso de falha, uma explicação do erro.
        String message
) {
    // Como um record, o compilador gera automaticamente para 'ItemProcessingResult':
    // 1. Um construtor público canônico que aceita 'code', 'line', 'success' e 'message'.
    // 2. Métodos de acesso para cada componente (ex: `code()`, `line()`, `success()`, `message()`).
    // 3. Implementações padrão e eficientes de `equals()`, `hashCode()` e `toString()` que consideram todos os componentes.
    // A imutabilidade e a concisão deste record o tornam ideal para representar resultados de operações
    // de forma clara e segura, especialmente em cenários de processamento em lote.
}