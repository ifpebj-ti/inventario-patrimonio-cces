package clp.inventory.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS global. Só as origens listadas em {@code cors.allowed-origins} (CORS_ALLOWED_ORIGINS) podem
 * chamar a API pelo navegador; a lista vazia não libera nenhuma origem.
 */
@Configuration
public class CorsConfig {

  private static final long PREFLIGHT_MAX_AGE_SECONDS = 3600;

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${cors.allowed-origins:}") List<String> allowedOrigins) {
    var configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(sanitize(allowedOrigins));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
    // Retry-After: tempo de bloqueio do login. Content-Disposition: nome do arquivo exportado.
    configuration.setExposedHeaders(
        List.of(HttpHeaders.RETRY_AFTER, HttpHeaders.CONTENT_DISPOSITION));
    // A autenticação é por Bearer no header; cookies e credenciais do navegador não são usados.
    configuration.setAllowCredentials(false);
    configuration.setMaxAge(PREFLIGHT_MAX_AGE_SECONDS);

    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  static List<String> sanitize(List<String> origins) {
    var sanitized = origins.stream().map(String::trim).filter(origin -> !origin.isEmpty()).toList();
    if (sanitized.contains("*")) {
      throw new IllegalStateException(
          "cors.allowed-origins não pode conter '*': liste as origens explicitamente.");
    }
    return sanitized;
  }
}
