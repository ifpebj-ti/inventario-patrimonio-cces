package clp.inventory.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

class CorsConfigTest {

  private CorsConfiguration configurationFor(List<String> origins) {
    var source = new CorsConfig().corsConfigurationSource(origins);
    return source.getCorsConfiguration(new MockHttpServletRequest("GET", "/inventory/qualquer"));
  }

  @Test
  void configuredOrigins_areAllowedAndCredentialsAreOff() {
    var configuration = configurationFor(List.of("https://inventariumifpe.me"));

    assertThat(configuration.checkOrigin("https://inventariumifpe.me"))
        .isEqualTo("https://inventariumifpe.me");
    assertThat(configuration.checkOrigin("https://evil.example")).isNull();
    assertThat(configuration.getAllowCredentials()).isFalse();
  }

  @Test
  void origins_areTrimmedAndBlanksIgnored() {
    var configuration =
        configurationFor(
            List.of(" https://inventariumifpe.me ", "", "  ", "http://localhost:3000"));

    assertThat(configuration.getAllowedOrigins())
        .containsExactly("https://inventariumifpe.me", "http://localhost:3000");
  }

  @Test
  void emptyList_allowsNoOrigin() {
    var configuration = configurationFor(List.of());

    assertThat(configuration.checkOrigin("http://localhost:3000")).isNull();
  }

  @Test
  void wildcardOrigin_isRejectedAtStartup() {
    assertThatThrownBy(() -> configurationFor(List.of("https://inventariumifpe.me", "*")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("*");
  }

  @Test
  void onlyBearerAndContentTypeHeadersAreAllowed_andRetryAfterIsExposed() {
    var configuration = configurationFor(List.of("https://inventariumifpe.me"));

    assertThat(configuration.getAllowedHeaders())
        .containsExactlyInAnyOrder("Authorization", "Content-Type");
    assertThat(configuration.getExposedHeaders()).contains("Retry-After", "Content-Disposition");
  }
}
