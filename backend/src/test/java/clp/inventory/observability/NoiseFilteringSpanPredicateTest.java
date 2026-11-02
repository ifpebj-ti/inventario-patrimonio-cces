package clp.inventory.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.exporter.FinishedSpan;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoiseFilteringSpanPredicateTest {

  private final NoiseFilteringSpanPredicate predicate = new NoiseFilteringSpanPredicate();

  private FinishedSpan span(String name, Map<String, String> tags) {
    FinishedSpan span = mock(FinishedSpan.class);
    when(span.getName()).thenReturn(name);
    when(span.getTags()).thenReturn(tags);
    return span;
  }

  @Test
  void exportsBusinessRequests() {
    assertThat(predicate.isExportable(span("http get /sectors", Map.of("uri", "/sectors"))))
        .isTrue();
    assertThat(
            predicate.isExportable(
                span("http post /auth/login", Map.of("uri", "/auth/login", "method", "POST"))))
        .isTrue();
  }

  @Test
  void dropsTheSpringSecurityFilterChainSpans() {
    assertThat(predicate.isExportable(span("security filterchain before", Map.of()))).isFalse();
    assertThat(predicate.isExportable(span("security filterchain after", Map.of()))).isFalse();
  }

  @Test
  void dropsTheActuatorRequestsOfTheMetricsScrapeAndHealthChecks() {
    assertThat(
            predicate.isExportable(
                span("http get /actuator/prometheus", Map.of("uri", "/actuator/prometheus"))))
        .isFalse();
    assertThat(predicate.isExportable(span("http get", Map.of("uri", "/actuator/health/liveness"))))
        .isFalse();
    assertThat(
            predicate.isExportable(
                span("http get", Map.of("http.url", "http://127.0.0.1:8081/actuator/health"))))
        .isFalse();
  }

  @Test
  void doesNotFailOnSpansWithoutNameOrTags() {
    FinishedSpan span = mock(FinishedSpan.class);
    when(span.getName()).thenReturn(null);
    when(span.getTags()).thenReturn(null);

    assertThat(predicate.isExportable(span)).isTrue();
  }
}
