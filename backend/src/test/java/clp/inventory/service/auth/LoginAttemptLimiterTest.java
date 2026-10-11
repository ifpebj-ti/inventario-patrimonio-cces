package clp.inventory.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {

  // Relógio que avança só quando o teste manda, para não dormir esperando a janela passar.
  static class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-10-11T12:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private MutableClock clock;
  private LoginAttemptLimiter limiter;

  @BeforeEach
  void setUp() {
    clock = new MutableClock();
    limiter = newLimiter(100, 3);
  }

  private LoginAttemptLimiter newLimiter(int ipMax, int userMax) {
    return new LoginAttemptLimiter(
        ipMax, userMax, Duration.ofMinutes(15), Duration.ofMinutes(15), clock);
  }

  private void fail(LoginAttemptLimiter target, String ip, String user, int times) {
    for (int i = 0; i < times; i++) {
      target.recordFailure(ip, user);
    }
  }

  @Test
  void belowTheLimit_isNotBlocked() {
    fail(limiter, "1.1.1.1", "admin", 2);

    assertThat(limiter.blockedFor("1.1.1.1", "admin")).isEmpty();
  }

  @Test
  void reachingTheUserLimit_blocksThatUserFromAnyIp() {
    fail(limiter, "1.1.1.1", "admin", 3);

    assertThat(limiter.blockedFor("9.9.9.9", "admin")).isPresent();
    assertThat(limiter.blockedFor("9.9.9.9", "outro")).isEmpty();
  }

  @Test
  void reachingTheIpLimit_blocksThatIpForAnyUser() {
    limiter = newLimiter(3, 100);

    limiter.recordFailure("1.1.1.1", "a");
    limiter.recordFailure("1.1.1.1", "b");
    limiter.recordFailure("1.1.1.1", "c");

    assertThat(limiter.blockedFor("1.1.1.1", "qualquer")).isPresent();
    assertThat(limiter.blockedFor("2.2.2.2", "qualquer")).isEmpty();
  }

  @Test
  void usernameIsNormalized() {
    limiter.recordFailure("1.1.1.1", " Admin ");
    limiter.recordFailure("1.1.1.1", "ADMIN");
    limiter.recordFailure("1.1.1.1", "admin");

    assertThat(limiter.blockedFor("9.9.9.9", "admin")).isPresent();
  }

  @Test
  void block_reportsTheRemainingTimeAndExpires() {
    fail(limiter, "1.1.1.1", "admin", 3);
    clock.advance(Duration.ofMinutes(10));

    assertThat(limiter.blockedFor("1.1.1.1", "admin")).contains(Duration.ofMinutes(5));

    clock.advance(Duration.ofMinutes(5));

    assertThat(limiter.blockedFor("1.1.1.1", "admin")).isEmpty();
  }

  @Test
  void afterTheBlock_theCounterStartsOver() {
    fail(limiter, "1.1.1.1", "admin", 3);
    clock.advance(Duration.ofMinutes(15));

    fail(limiter, "1.1.1.1", "admin", 2);

    assertThat(limiter.blockedFor("1.1.1.1", "admin")).isEmpty();
  }

  @Test
  void failuresOutsideTheWindow_doNotAccumulate() {
    fail(limiter, "1.1.1.1", "admin", 2);
    clock.advance(Duration.ofMinutes(16));
    fail(limiter, "1.1.1.1", "admin", 2);

    assertThat(limiter.blockedFor("1.1.1.1", "admin")).isEmpty();
  }

  @Test
  void success_resetsTheUserCounterButNotTheIpCounter() {
    limiter = newLimiter(4, 3);

    fail(limiter, "1.1.1.1", "admin", 2);
    limiter.recordSuccess("admin");
    fail(limiter, "1.1.1.1", "admin", 2);

    // usuário: 2 falhas depois do reset, abaixo do limite de 3
    assertThat(limiter.blockedFor("9.9.9.9", "admin")).isEmpty();
    // IP: 4 falhas no total, o sucesso não zera
    assertThat(limiter.blockedFor("1.1.1.1", "outro")).isPresent();
  }

  @Test
  void blankOrNullUsername_doesNotBreak() {
    limiter.recordFailure("1.1.1.1", null);

    assertThat(limiter.blockedFor("1.1.1.1", null)).isEmpty();
  }

  @Test
  void clear_dropsEveryBlock() {
    fail(limiter, "1.1.1.1", "admin", 3);

    limiter.clear();

    assertThat(limiter.blockedFor("1.1.1.1", "admin")).isEmpty();
  }

  @Test
  void manyDistinctKeys_doNotGrowWithoutBound_andKeepActiveBlocks() {
    fail(limiter, "1.1.1.1", "admin", 3);

    for (int i = 0; i < 25_000; i++) {
      limiter.recordFailure("10.0." + (i / 250) + "." + (i % 250), "usuario-" + i);
    }

    assertThat(limiter.blockedFor("1.1.1.1", "admin")).isPresent();
  }
}
