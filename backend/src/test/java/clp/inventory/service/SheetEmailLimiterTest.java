package clp.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SheetEmailLimiterTest {

  // Relógio que avança só quando o teste manda, para não dormir esperando a janela passar.
  static class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-11-15T12:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private MutableClock clock;
  private SheetEmailLimiter limiter;

  @BeforeEach
  void setUp() {
    clock = new MutableClock();
    limiter = new SheetEmailLimiter(3, Duration.ofHours(1), clock);
  }

  @Test
  void ateOLimiteNaoBloqueia() {
    assertThat(limiter.tryAcquire(1)).isEmpty();
    assertThat(limiter.tryAcquire(1)).isEmpty();
    assertThat(limiter.tryAcquire(1)).isEmpty();
  }

  @Test
  void passouDoLimiteBloqueiaEInformaQuantoFalta() {
    for (int i = 0; i < 3; i++) {
      limiter.tryAcquire(1);
    }
    clock.advance(Duration.ofMinutes(20));

    assertThat(limiter.tryAcquire(1)).contains(Duration.ofMinutes(40));
  }

  @Test
  void envioBloqueadoNaoContaNaJanela() {
    for (int i = 0; i < 3; i++) {
      limiter.tryAcquire(1);
    }
    for (int i = 0; i < 5; i++) {
      limiter.tryAcquire(1);
    }
    clock.advance(Duration.ofHours(1));

    assertThat(limiter.tryAcquire(1)).isEmpty();
  }

  @Test
  void aJanelaVirandoLiberaDeNovo() {
    for (int i = 0; i < 3; i++) {
      limiter.tryAcquire(1);
    }
    assertThat(limiter.tryAcquire(1)).isPresent();

    clock.advance(Duration.ofHours(1));

    assertThat(limiter.tryAcquire(1)).isEmpty();
  }

  @Test
  void cadaUsuarioTemOSeuLimite() {
    for (int i = 0; i < 3; i++) {
      limiter.tryAcquire(1);
    }

    assertThat(limiter.tryAcquire(1)).isPresent();
    assertThat(limiter.tryAcquire(2)).isEmpty();
  }

  @Test
  void clearEsvaziaOsContadores() {
    for (int i = 0; i < 3; i++) {
      limiter.tryAcquire(1);
    }

    limiter.clear();

    assertThat(limiter.tryAcquire(1)).isEmpty();
  }
}
