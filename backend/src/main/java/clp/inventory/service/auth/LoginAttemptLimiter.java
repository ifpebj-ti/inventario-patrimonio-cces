package clp.inventory.service.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bloqueio temporário do login por senha contra força bruta, com contador em memória por usuário.
 *
 * <p>Cada usuário acumula falhas dentro de uma janela; ao atingir o limite ele fica bloqueado por
 * um tempo fixo, de qualquer IP. O bloqueio é temporário de propósito: um bloqueio permanente
 * deixaria qualquer um travar a conta de sistema para sempre. Falhas de usuários inexistentes
 * contam do mesmo jeito, para a resposta não revelar quais usuários existem.
 *
 * <p>O limite por IP não é daqui: fica no Nginx (zona {@code auth}, só no {@code /auth/login}), que
 * é quem enxerga o IP real do cliente.
 *
 * <p>O estado vive só neste processo (a aplicação roda numa instância) e reiniciar o backend zera
 * os contadores.
 */
@Component
public class LoginAttemptLimiter {

  private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);

  // Teto de chaves do mapa, para que usuários aleatórios não encham a memória.
  private static final int MAX_KEYS = 10_000;
  private static final int MAX_USERNAME_LENGTH = 128;

  private final Map<String, Entry> byUser = new ConcurrentHashMap<>();

  private final int userMaxFailures;
  private final long windowMillis;
  private final long blockMillis;
  private final Clock clock;

  @Autowired
  public LoginAttemptLimiter(
      @Value("${auth.login.user-max-failures:5}") int userMaxFailures,
      @Value("${auth.login.window:15m}") Duration window,
      @Value("${auth.login.block-duration:15m}") Duration blockDuration) {
    this(userMaxFailures, window, blockDuration, Clock.systemUTC());
  }

  LoginAttemptLimiter(int userMaxFailures, Duration window, Duration blockDuration, Clock clock) {
    this.userMaxFailures = userMaxFailures;
    this.windowMillis = window.toMillis();
    this.blockMillis = blockDuration.toMillis();
    this.clock = clock;
  }

  /** Quanto falta para o usuário poder tentar de novo; vazio se ele não está bloqueado. */
  public Optional<Duration> blockedFor(String username) {
    long remaining = remainingBlock(userKey(username), clock.millis());

    return remaining > 0 ? Optional.of(Duration.ofMillis(remaining)) : Optional.empty();
  }

  public void recordFailure(String username) {
    registerFailure(userKey(username), clock.millis());
  }

  /** Um login com sucesso zera o contador de falhas do usuário. */
  public void recordSuccess(String username) {
    byUser.remove(userKey(username));
  }

  /** Esvazia os contadores. Existe para os testes não herdarem bloqueios uns dos outros. */
  public void clear() {
    byUser.clear();
  }

  private long remainingBlock(String key, long now) {
    Entry entry = byUser.get(key);

    return entry == null ? 0 : entry.blockedUntil - now;
  }

  private void registerFailure(String key, long now) {
    if (byUser.size() >= MAX_KEYS && !byUser.containsKey(key)) {
      makeRoom(now);
    }

    byUser.compute(
        key,
        (k, current) -> {
          Entry entry = current == null ? new Entry(now) : current;

          if (now - entry.windowStart > windowMillis) {
            entry.windowStart = now;
            entry.failures = 0;
          }

          entry.failures++;

          if (entry.failures >= userMaxFailures) {
            entry.blockedUntil = now + blockMillis;
            entry.windowStart = now;
            entry.failures = 0;
            log.warn("Login por senha bloqueado por excesso de falhas (user={})", safe(k));
          }

          return entry;
        });
  }

  // Remove o que já expirou; se ainda assim estiver cheio, descarta as chaves mais antigas que
  // não estão bloqueadas, para nunca derrubar um bloqueio ativo em favor de uma chave nova.
  private void makeRoom(long now) {
    byUser.entrySet().removeIf(e -> e.getValue().isExpired(now, windowMillis));

    if (byUser.size() < MAX_KEYS) {
      return;
    }

    byUser.entrySet().stream()
        .filter(e -> e.getValue().blockedUntil <= now)
        .sorted(Comparator.comparingLong(e -> e.getValue().windowStart))
        .limit(Math.max(1, MAX_KEYS / 10))
        .map(Map.Entry::getKey)
        .toList()
        .forEach(byUser::remove);
  }

  private static String userKey(String username) {
    String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);

    return normalized.length() > MAX_USERNAME_LENGTH
        ? normalized.substring(0, MAX_USERNAME_LENGTH)
        : normalized;
  }

  // O identificador vem do cliente; sem isso uma quebra de linha forjaria linhas de log.
  private static String safe(String key) {
    return key.replaceAll("[^a-zA-Z0-9._@:-]", "?");
  }

  private static final class Entry {
    long windowStart;
    int failures;
    long blockedUntil;

    Entry(long now) {
      this.windowStart = now;
    }

    boolean isExpired(long now, long windowMillis) {
      return blockedUntil <= now && now - windowStart > windowMillis;
    }
  }
}
