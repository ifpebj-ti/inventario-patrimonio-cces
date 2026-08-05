package clp.inventory.security;

// Importa anotações do Spring para configuração e gerenciamento de beans.
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
// Importa HttpMethod para especificar tipos de requisições HTTP.
import org.springframework.http.HttpMethod;
// Importa classes do Spring Security para configuração de segurança HTTP.
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // Para codificação de senhas.
import org.springframework.security.crypto.password.PasswordEncoder;     // Interface para codificação de senhas.
import org.springframework.security.web.SecurityFilterChain;             // Para definir a cadeia de filtros de segurança.
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter; // Para adicionar filtro antes da autenticação básica.

// Anotação que indica que esta classe contém métodos de configuração para o Spring.
@Configuration
public class SecurityConfig {

    // Declara uma dependência para o SecurityFilter, que será responsável por validar tokens JWT.
    private final SecurityFilter securityFilter;

    // Construtor que recebe o SecurityFilter via injeção de dependência do Spring.
    public SecurityConfig(SecurityFilter securityFilter) {
        this.securityFilter = securityFilter;
    }

    // Anotação @Bean indica que o método produz um bean gerenciado pelo Spring.
    // Este método configura a cadeia de filtros de segurança HTTP.
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Desabilita a proteção CSRF (Cross-Site Request Forgery) para permitir requisições de outras origens,
        // comum em APIs RESTful que usam autenticação baseada em token (JWT).
        http.csrf(AbstractHttpConfigurer::disable)
                // Habilita a configuração de CORS (Cross-Origin Resource Sharing) com as configurações padrão do Spring.
                .cors(Customizer.withDefaults())
                // Configura as regras de autorização para requisições HTTP.
                .authorizeHttpRequests(auth -> {
                    auth
                            // Permite que requisições para "/new-user" sejam acessíveis por qualquer um (sem autenticação).
                            .requestMatchers("/new-user").permitAll()
                            // Permite que requisições para "/auth/login" sejam acessíveis por qualquer um.
                            .requestMatchers("/auth/login").permitAll()
                            // Permite que requisições para "/auth/verify/{token}" (verificação de e-mail) sejam acessíveis por qualquer um.
                            .requestMatchers("/auth/verify/{token}").permitAll()
                            // Permite que requisições GET para "/auth/reset-password" (solicitação de reset de senha) sejam acessíveis por qualquer um.
                            .requestMatchers("/auth/reset-password").permitAll()
                            // Permite que requisições PUT para "/auth/reset-password/{token}" (redefinição de senha) sejam acessíveis por qualquer um.
                            .requestMatchers(HttpMethod.PUT, "/auth/reset-password/{token}").permitAll();
                    // Todas as outras requisições (anyRequest) exigem autenticação (authenticated).
                    auth.anyRequest().authenticated();
                })
//                TODO: Verificar se tem necessidade futuramente com os tipos de planos disponíveis.
//                O bloco abaixo está comentado e pode ser um trecho futuro para tratamento de exceções de autenticação,
//                possivelmente para personalizar respostas de acesso não autorizado, como retornar JSON em vez de uma página de erro padrão.
//                .exceptionHandling(handling -> handling
//                        .authenticationEntryPoint((request, response, authException) -> {
//                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//                            response.setContentType("application/json");
//                            response.setHeader("WWW-Authenticate", "Bearer");
//                            response.getWriter().write("{\"error\":\"Acesso não autorizado\"}");
//                        })
//                )
                // Adiciona o SecurityFilter antes do BasicAuthenticationFilter na cadeia de filtros do Spring Security.
                // Isso garante que o filtro de segurança personalizado (para validação de JWT) seja executado antes dos filtros de autenticação padrão.
                .addFilterBefore(securityFilter, BasicAuthenticationFilter.class);

        // Constrói e retorna a cadeia de filtros de segurança configurada.
        return http.build();
    }

    // Anotação @Bean indica que o método produz um bean gerenciado pelo Spring.
    // Este método define o bean PasswordEncoder, que será usado para codificar e verificar senhas.
    @Bean
    public PasswordEncoder passwordEncoder() {
        // Retorna uma instância de BCryptPasswordEncoder, que é um forte algoritmo de hashing de senha.
        return new BCryptPasswordEncoder();
    }
}