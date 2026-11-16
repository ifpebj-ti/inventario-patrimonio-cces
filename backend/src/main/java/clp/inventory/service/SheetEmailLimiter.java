package clp.inventory.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limite de envios da planilha por e-mail, por usuário, numa janela fixa ({@code
 * email.sheet.max-per-window} envios a cada {@code email.sheet.window}).
 *
 * <p>O estado vive só neste processo, como o do {@code LoginAttemptLimiter}: reiniciar o backend
 * zera os contadores e, com mais de uma instância, cada uma conta o seu.
 */
@Component
public class SheetEmailLimiter {

  // Teto de chaves do mapa, para que o mapa não cresça sem limite.
  private static final int MAX_KEYS = 10_000;

  private final Map<Long, Window> byUser = new ConcurrentHashMap<>();

  private final int maxPerWindow;
  private final long windowMillis;
  private final Clock clock;

  @Autowired
  public SheetEmailLimiter(
      @Value("${email.sheet.max-per-window:10}") int maxPerWindow,
      @Value("${email.sheet.window:1h}") Duration window) {
    this(maxPerWindow, window, Clock.systemUTC());
  }

  SheetEmailLimiter(int maxPerWindow, Duration window, Clock clock) {
    this.maxPerWindow = maxPerWindow;
    this.windowMillis = window.toMillis();
    this.clock = clock;
  }

  /**
   * Conta um envio do usuário. Vazio se ele ainda cabe no limite; senão, quanto falta para a janela
   * virar (e o envio não é contado).
   */
  public Optional<Duration> tryAcquire(long userId) {
    long now = clock.millis();
    if (byUser.size() >= MAX_KEYS && !byUser.containsKey(userId)) {
      byUser.entrySet().removeIf(e -> now - e.getValue().start >= windowMillis);
    }

    long[] retryAfter = {0};
    byUser.compute(
        userId,
        (key, current) -> {
          Window window = current;
          if (window == null || now - window.start >= windowMillis) {
            window = new Window(now);
          }
          if (window.count >= maxPerWindow) {
            retryAfter[0] = window.start + windowMillis - now;
          } else {
            window.count++;
          }
          return window;
        });

    return retryAfter[0] > 0 ? Optional.of(Duration.ofMillis(retryAfter[0])) : Optional.empty();
  }

  /** Esvazia os contadores. Existe para os testes não herdarem o limite uns dos outros. */
  public void clear() {
    byUser.clear();
  }

  private static final class Window {
    final long start;
    int count;

    Window(long start) {
      this.start = start;
    }
  }
}
