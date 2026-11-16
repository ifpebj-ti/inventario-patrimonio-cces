package clp.inventory.service;

/** O e-mail não foi entregue ao servidor SMTP. A causa fica no log; a mensagem é genérica. */
public class EmailDeliveryException extends RuntimeException {

  public EmailDeliveryException(Throwable cause) {
    super("Não foi possível enviar o e-mail.", cause);
  }
}
