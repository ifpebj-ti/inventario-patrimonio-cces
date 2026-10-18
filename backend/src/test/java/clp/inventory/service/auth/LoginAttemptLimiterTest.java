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
    limiter = newLimiter(3);
  }

  private LoginAttemptLimiter newLimiter(int userMax) {
    return new LoginAttemptLimiter(userMax, Duration.ofMinutes(15), Duration.ofMinutes(15), clock);
  }

  private void fail(LoginAttemptLimiter target, String user, int times) {
    for (int i = 0; i < times; i++) {
      target.recordFailure(user);
    }
  }

  @Test
  void belowTheLimit_isNotBlocked() {
    fail(limiter, "admin", 2);

    assertThat(limiter.blockedFor("admin")).isEmpty();
  }

  @Test
  void reachingTheUserLimit_blocksThatUser() {
    fail(limiter, "admin", 3);

    assertThat(limiter.blockedFor("admin")).isPresent();
    assertThat(limiter.blockedFor("outro")).isEmpty();
  }

  @Test
  void usernameIsNormalized() {
    limiter.recordFailure(" Admin ");
    limiter.recordFailure("ADMIN");
    limiter.recordFailure("admin");

    assertThat(limiter.blockedFor("admin")).isPresent();
  }

  @Test
  void block_reportsTheRemainingTimeAndExpires() {
    fail(limiter, "admin", 3);
    clock.advance(Duration.ofMinutes(10));

    assertThat(limiter.blockedFor("admin")).contains(Duration.ofMinutes(5));

    clock.advance(Duration.ofMinutes(5));

    assertThat(limiter.blockedFor("admin")).isEmpty();
  }

  @Test
  void afterTheBlock_theCounterStartsOver() {
    fail(limiter, "admin", 3);
    clock.advance(Duration.ofMinutes(15));

    fail(limiter, "admin", 2);

    assertThat(limiter.blockedFor("admin")).isEmpty();
  }

  @Test
  void failuresOutsideTheWindow_doNotAccumulate() {
    fail(limiter, "admin", 2);
    clock.advance(Duration.ofMinutes(16));
    fail(limiter, "admin", 2);

    assertThat(limiter.blockedFor("admin")).isEmpty();
  }

  @Test
  void success_resetsTheUserCounter() {
    fail(limiter, "admin", 2);
    limiter.recordSuccess("admin");
    fail(limiter, "admin", 2);

    assertThat(limiter.blockedFor("admin")).isEmpty();
  }

  @Test
  void blankOrNullUsername_doesNotBreak() {
    limiter.recordFailure(null);

    assertThat(limiter.blockedFor(null)).isEmpty();
  }

  @Test
  void clear_dropsEveryBlock() {
    fail(limiter, "admin", 3);

    limiter.clear();

    assertThat(limiter.blockedFor("admin")).isEmpty();
  }

  @Test
  void manyDistinctKeys_doNotGrowWithoutBound_andKeepActiveBlocks() {
    fail(limiter, "admin", 3);

    for (int i = 0; i < 25_000; i++) {
      limiter.recordFailure("usuario-" + i);
    }

    assertThat(limiter.blockedFor("admin")).isPresent();
  }
}
