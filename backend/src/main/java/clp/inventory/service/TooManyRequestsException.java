package clp.inventory.service;

import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 429 com o {@code Retry-After} (em segundos) até o limite liberar. */
public class TooManyRequestsException extends ResponseStatusException {

  private final long retryAfterSeconds;

  public TooManyRequestsException(String reason, Duration retryAfter) {
    super(HttpStatus.TOO_MANY_REQUESTS, reason);
    // Arredonda para cima: dizer 0s faria o cliente tentar de novo e ser barrado outra vez.
    this.retryAfterSeconds = Math.max(1, (retryAfter.toMillis() + 999) / 1000);
  }

  @Override
  public HttpHeaders getHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
    return headers;
  }
}
