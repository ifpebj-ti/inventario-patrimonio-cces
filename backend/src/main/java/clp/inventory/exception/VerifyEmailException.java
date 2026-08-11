package clp.inventory.exception;

// Define uma nova classe de exceção chamada 'VerifyEmailException'.
// Esta classe estende 'Exception', o que a torna uma exceção checada (checked exception).
// Isso significa que, em Java, os métodos que podem lançar esta exceção devem
// declará-la em sua cláusula 'throws', e os chamadores desses métodos devem
// obrigatoriamente capturá-la (usando um bloco try-catch) ou relançá-la.
// Exceções checadas são tipicamente usadas para condições recuperáveis que um
// cliente pode ser capaz de lidar.
public class VerifyEmailException extends Exception {

    // Construtor para a exceção 'VerifyEmailException'.
    // Ele recebe uma String 'message' como argumento.
    public VerifyEmailException(String message) {
        // Chama o construtor da superclasse 'Exception' passando a 'message'.
        // Isso garante que a mensagem de erro seja armazenada na exceção e possa
        // ser recuperada posteriormente (por exemplo, através do método getMessage()).
        super(message);
    }
}