package clp.inventory.config;

import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz o que o banco recusa em resposta legível (RFC 7807, application/problem+json). Hoje só a
 * violação de chave estrangeira: apagar algo de que outra tabela depende (por exemplo um item com
 * histórico de validação) é conflito com o estado atual, não erro interno. O serviço confere antes
 * de tocar no banco; isto é a rede de segurança para o que escapar, como uma corrida.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  // SQLSTATE 23503 = foreign_key_violation.
  private static final String FOREIGN_KEY_VIOLATION = "23503";

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ProblemDetail handleDataIntegrity(DataIntegrityViolationException e) {
    if (!isForeignKeyViolation(e)) {
      // Outras violações (nulo, único) continuam com o tratamento de quem as espera ou viram 500.
      throw e;
    }

    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "O registro possui dependências (por exemplo, histórico de validação) e não pode ser"
                + " excluído.");
    problem.setTitle("Conflito com dados relacionados");
    return problem;
  }

  static boolean isForeignKeyViolation(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql && FOREIGN_KEY_VIOLATION.equals(sql.getSQLState())) {
        return true;
      }
      if (cause.getCause() == cause) {
        break;
      }
    }
    return false;
  }
}
