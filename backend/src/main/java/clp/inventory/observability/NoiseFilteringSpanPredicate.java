package clp.inventory.observability;

import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.exporter.SpanExportingPredicate;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Impede que spans sem valor cheguem ao Tempo. O Alloy lê {@code /actuator/prometheus} a cada 30
 * segundos e o Docker/Nginx consultam o health: sem este filtro cada leitura vira um trace e, em
 * poucos dias, as leituras superam as requisições reais. Os spans do filtro do Spring Security
 * também aparecem como traces soltos, sem a requisição HTTP como pai.
 */
@Component
public class NoiseFilteringSpanPredicate implements SpanExportingPredicate {

  private static final String ACTUATOR = "/actuator";
  private static final String SECURITY_FILTER_CHAIN = "security filterchain";

  @Override
  public boolean isExportable(FinishedSpan span) {
    String name = span.getName();
    if (name != null && (name.startsWith(SECURITY_FILTER_CHAIN) || name.contains(ACTUATOR))) {
      return false;
    }

    Map<String, String> tags = span.getTags();
    if (tags == null) {
      return true;
    }
    for (String key : new String[] {"uri", "http.url", "url.path"}) {
      String value = tags.get(key);
      if (value != null && value.contains(ACTUATOR)) {
        return false;
      }
    }
    return true;
  }
}
