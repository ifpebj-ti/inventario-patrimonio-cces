package clp.inventory.security;

import clp.inventory.model.User;
import clp.inventory.providers.JWTProvider;
import clp.inventory.providers.SessionToken;
import clp.inventory.repository.UserRepository;
import clp.inventory.service.auth.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SecurityFilter extends OncePerRequestFilter {

  private final JWTProvider jwtProvider;
  private final UserRepository userRepository;
  private final SessionService sessionService;

  public SecurityFilter(
      JWTProvider jwtProvider, UserRepository userRepository, SessionService sessionService) {
    this.jwtProvider = jwtProvider;
    this.userRepository = userRepository;
    this.sessionService = sessionService;
  }

  /**
   * O filtro roda mesmo nas rotas liberadas e aborta com 401 diante de um Bearer inválido. Como o
   * frontend anexa o token em toda requisição, quem está com o JWT expirado não conseguiria fazer
   * login de novo sem esta exceção.
   *
   * <p>Espelha o permitAll do SecurityConfig e não pode ser generalizado para "/auth/**": /auth/me
   * exige autenticação e depende deste filtro para obtê-la.
   */
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getServletPath().equals("/auth/google")
        || request.getServletPath().equals("/auth/login")
        || request.getServletPath().startsWith("/actuator/")
        || request.getServletPath().startsWith("/v3/api-docs")
        || request.getServletPath().startsWith("/swagger-ui");
  }

  private User findUser(String subject) {
    try {
      return userRepository.findById(Long.parseLong(subject)).orElse(null);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private void reject(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json");
    response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
    response.getWriter().write("{\"error\":\"" + message + "\"}");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");

    if (header != null && header.startsWith("Bearer ")) {
      var parsed = jwtProvider.parse(header);
      if (parsed.isEmpty()) {
        reject(response, "Token inválido");
        return;
      }

      SessionToken token = parsed.get();
      User user = findUser(token.subject());
      if (user != null && !user.isActive()) {
        // Desativar a conta derruba as sessões já abertas, não só novos logins.
        reject(response, "Conta desativada");
        return;
      }
      // Versão diferente: a conta trocou de credencial (ou foi desativada) depois da emissão.
      // Token com jti na denylist: a sessão foi encerrada pelo logout.
      if ((user != null && user.getTokenVersion() != token.version())
          || sessionService.isRevoked(token)) {
        reject(response, "Sessão encerrada");
        return;
      }

      request.setAttribute("id_user", token.subject());
      UsernamePasswordAuthenticationToken authenticationToken =
          new UsernamePasswordAuthenticationToken(token.subject(), null, Collections.emptyList());
      SecurityContextHolder.getContext().setAuthentication(authenticationToken);
    }

    filterChain.doFilter(request, response);
  }
}
