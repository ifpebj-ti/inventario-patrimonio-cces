package clp.inventory.security;

// Importa o provedor de JWT personalizado.
import clp.inventory.providers.JWTProvider;
// Importa classes do Jakarta Servlet API para lidar com filtros, requisições e respostas HTTP.
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
// Importa classes do Spring Security para autenticação e contexto de segurança.
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component; // Anotação para marcar a classe como um componente Spring.
import org.springframework.web.filter.OncePerRequestFilter; // Filtro que garante execução uma vez por requisição.

// Importa classes para operações de I/O e coleções.
import java.io.IOException;
import java.util.Collections;

// Anotação que marca esta classe como um componente Spring.
// Isso significa que o Spring a gerenciará como um bean e poderá injetá-la em outros componentes.
@Component
// Esta classe estende OncePerRequestFilter, garantindo que o filtro seja executado apenas uma vez
// por requisição HTTP, mesmo que a requisição seja encaminhada internamente várias vezes (por exemplo, para um Servlet ou outro filtro).
public class SecurityFilter extends OncePerRequestFilter {

    // Declara uma dependência para o JWTProvider, que é responsável por validar os tokens JWT.
    private final JWTProvider jwtProvider;

    // Construtor da classe SecurityFilter.
    // O Spring injetará automaticamente uma instância de JWTProvider quando criar este filtro.
    public SecurityFilter(JWTProvider jwtProvider) {
        this.jwtProvider = jwtProvider; // Inicializa a dependência do JWTProvider.
    }

    /**
     * Este método é a lógica principal do filtro. Ele é invocado para cada requisição HTTP.
     *
     * @param request O objeto HttpServletRequest que contém os detalhes da requisição.
     * @param response O objeto HttpServletResponse para manipular a resposta da requisição.
     * @param filterChain O FilterChain para passar a requisição e a resposta para o próximo filtro na cadeia.
     * @throws ServletException Em caso de erro de servlet.
     * @throws IOException Em caso de erro de I/O.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, // A requisição HTTP recebida.
            HttpServletResponse response, // A resposta HTTP a ser enviada.
            FilterChain filterChain // A cadeia de filtros para continuar o processamento.
    ) throws ServletException, IOException {
        // Tenta obter o cabeçalho "Authorization" da requisição.
        String header = request.getHeader("Authorization");

        // Verifica se o cabeçalho de autorização não é nulo e começa com "Bearer ".
        // Isso indica que um token JWT pode estar presente.
        if (header != null && header.startsWith("Bearer ")) {
            // Se um token é encontrado, ele é passado para o JWTProvider para validação.
            var subjectToken = jwtProvider.validateToken(header);
            // Se o 'subjectToken' retornado for vazio, significa que o token é inválido ou expirado.
            if (subjectToken.isEmpty()) {
                // Define o status da resposta HTTP para 401 Unauthorized.
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                // Define o tipo de conteúdo da resposta como JSON.
                response.setContentType("application/json");
                // Adiciona um cabeçalho WWW-Authenticate para informar o cliente sobre o tipo de erro do token.
                response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
                // Escreve uma mensagem de erro JSON no corpo da resposta.
                response.getWriter().write("{\"error\":\"Token inválido\"}");
                return; // Interrompe a cadeia de filtros e envia a resposta de erro.
            } else {
                // Se o token for válido e o 'subjectToken' for extraído com sucesso,
                // define o ID do usuário (subjectToken) como um atributo na requisição.
                // Isso permite que controladores e outros componentes acessem o ID do usuário autenticado.
                request.setAttribute("id_user", subjectToken);
                // Cria um objeto UsernamePasswordAuthenticationToken.
                // O 'subjectToken' (ID do usuário) é usado como principal (usuário autenticado),
                // a senha é nula (já que a autenticação foi por token, não por senha),
                // e as autoridades são uma lista vazia (pois as permissões podem ser obtidas posteriormente, se necessário).
                UsernamePasswordAuthenticationToken authenticationToken =
                        new UsernamePasswordAuthenticationToken(subjectToken, null, Collections.emptyList());
                // Define o objeto de autenticação no SecurityContextHolder.
                // Isso informa ao Spring Security que a requisição atual está autenticada
                // com o principal fornecido, e o usuário agora é considerado autenticado para esta requisição.
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
        }

        // Continua a cadeia de filtros.
        // Se o cabeçalho de autorização não estiver presente ou não for um token Bearer,
        // ou se o token for validado, a requisição é passada para o próximo filtro ou para o endpoint.
        filterChain.doFilter(request, response);
    }
}