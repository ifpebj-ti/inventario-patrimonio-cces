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
 * Bloqueio temporário do login por senha contra força bruta, com contadores em memória por IP e por
 * usuário.
 *
 * <p>Cada chave acumula falhas dentro de uma janela; ao atingir o limite ela fica bloqueada por um
 * tempo fixo. O bloqueio é temporário de propósito: um bloqueio permanente por usuário deixaria
 * qualquer um travar a conta de sistema para sempre. Falhas de usuários inexistentes contam do
 * mesmo jeito, para a resposta não revelar quais usuários existem.
 *
 * <p>O estado vive só neste processo (a aplicação roda numa instância) e reiniciar o backend zera
 * os contadores.
 */
@Component
public class LoginAttemptLimiter {

  private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);

  // Teto de chaves por mapa, para que IPs ou usuários aleatórios não encham a memória.
  private static final int MAX_KEYS = 10_000;
  private static final int MAX_USERNAME_LENGTH = 128;

  private final Map<String, Entry> byIp = new ConcurrentHashMap<>();
  private final Map<String, Entry> byUser = new ConcurrentHashMap<>();

  private final int ipMaxFailures;
  private final int userMaxFailures;
  private final long windowMillis;
  private final long blockMillis;
  private final Clock clock;

  @Autowired
  public LoginAttemptLimiter(
      @Value("${auth.login.ip-max-failures:10}") int ipMaxFailures,
      @Value("${auth.login.user-max-failures:5}") int userMaxFailures,
      @Value("${auth.login.window:15m}") Duration window,
      @Value("${auth.login.block-duration:15m}") Duration blockDuration) {
    this(ipMaxFailures, userMaxFailures, window, blockDuration, Clock.systemUTC());
  }

  LoginAttemptLimiter(
      int ipMaxFailures,
      int userMaxFailures,
      Duration window,
      Duration blockDuration,
      Clock clock) {
    this.ipMaxFailures = ipMaxFailures;
    this.userMaxFailures = userMaxFailures;
    this.windowMillis = window.toMillis();
    this.blockMillis = blockDuration.toMillis();
    this.clock = clock;
  }

  /** Quanto falta para o IP ou o usuário poderem tentar de novo; vazio se nenhum está bloqueado. */
  public Optional<Duration> blockedFor(String ip, String username) {
    long now = clock.millis();
    long remaining =
        Math.max(remainingBlock(byIp, ip, now), remainingBlock(byUser, userKey(username), now));

    return remaining > 0 ? Optional.of(Duration.ofMillis(remaining)) : Optional.empty();
  }

  public void recordFailure(String ip, String username) {
    long now = clock.millis();

    registerFailure(byIp, ip, ipMaxFailures, now, "ip");
    registerFailure(byUser, userKey(username), userMaxFailures, now, "user");
  }

  /**
   * Zera só o contador do usuário. O do IP fica: senão quem erra senhas de várias contas poderia
   * "lavar" o contador entrando numa conta que é dele.
   */
  public void recordSuccess(String username) {
    byUser.remove(userKey(username));
  }

  /** Esvazia os contadores. Existe para os testes não herdarem bloqueios uns dos outros. */
  public void clear() {
    byIp.clear();
    byUser.clear();
  }

  private long remainingBlock(Map<String, Entry> entries, String key, long now) {
    Entry entry = entries.get(key);

    return entry == null ? 0 : entry.blockedUntil - now;
  }

  private void registerFailure(
      Map<String, Entry> entries, String key, int maxFailures, long now, String scope) {
    if (entries.size() >= MAX_KEYS && !entries.containsKey(key)) {
      makeRoom(entries, now);
    }

    entries.compute(
        key,
        (k, current) -> {
          Entry entry = current == null ? new Entry(now) : current;

          if (now - entry.windowStart > windowMillis) {
            entry.windowStart = now;
            entry.failures = 0;
          }

          entry.failures++;

          if (entry.failures >= maxFailures) {
            entry.blockedUntil = now + blockMillis;
            entry.windowStart = now;
            entry.failures = 0;
            log.warn(
                "Login por senha bloqueado por excesso de falhas (scope={}, key={})",
                scope,
                safe(k));
          }

          return entry;
        });
  }

  // Remove o que já expirou; se ainda assim estiver cheio, descarta as chaves mais antigas que
  // não estão bloqueadas, para nunca derrubar um bloqueio ativo em favor de uma chave nova.
  private void makeRoom(Map<String, Entry> entries, long now) {
    entries.entrySet().removeIf(e -> e.getValue().isExpired(now, windowMillis));

    if (entries.size() < MAX_KEYS) {
      return;
    }

    entries.entrySet().stream()
        .filter(e -> e.getValue().blockedUntil <= now)
        .sorted(Comparator.comparingLong(e -> e.getValue().windowStart))
        .limit(Math.max(1, MAX_KEYS / 10))
        .map(Map.Entry::getKey)
        .toList()
        .forEach(entries::remove);
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
