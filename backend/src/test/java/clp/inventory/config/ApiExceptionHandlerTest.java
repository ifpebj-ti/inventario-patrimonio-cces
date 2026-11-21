package clp.inventory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class ApiExceptionHandlerTest {

  private final ApiExceptionHandler handler = new ApiExceptionHandler();

  @Test
  void foreignKeyViolationBecomesAReadableConflictProblem() {
    var error =
        new DataIntegrityViolationException(
            "could not execute statement", new SQLException("violates foreign key", "23503"));

    var problem = handler.handleDataIntegrity(error);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(problem.getTitle()).isEqualTo("Conflito com dados relacionados");
    assertThat(problem.getDetail()).contains("histórico de validação");
  }

  @Test
  void foreignKeyViolationIsFoundDeepInTheCauseChain() {
    var root = new SQLException("violates foreign key", "23503");
    var error = new DataIntegrityViolationException("outer", new RuntimeException("middle", root));

    assertThat(handler.handleDataIntegrity(error).getStatus()).isEqualTo(409);
  }

  @Test
  void otherIntegrityViolationsAreNotSwallowed() {
    var notNull =
        new DataIntegrityViolationException(
            "null", new SQLException("not null violation", "23502"));
    var noCause = new DataIntegrityViolationException("sem causa");

    assertThatThrownBy(() -> handler.handleDataIntegrity(notNull)).isSameAs(notNull);
    assertThatThrownBy(() -> handler.handleDataIntegrity(noCause)).isSameAs(noCause);
  }
}
