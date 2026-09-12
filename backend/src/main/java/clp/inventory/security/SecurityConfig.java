package clp.inventory.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final SecurityFilter securityFilter;

    public SecurityConfig(SecurityFilter securityFilter) {
        this.securityFilter = securityFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                // Autenticação é só por Bearer: sessão persistida somada a CSRF desabilitado
                // e origins="*" seria o que abriria espaço para CSRF de verdade.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth
                            // Sem isto, o encaminhamento para /error de qualquer exceção não
                            // tratada é barrado e o cliente recebe 403 vazio no lugar do 500.
                            .requestMatchers("/error").permitAll()
                            .requestMatchers("/actuator/health/**").permitAll()
                            // Sem HttpMethod: o preflight OPTIONS precisa casar também.
                            .requestMatchers("/auth/google").permitAll();
                    auth.anyRequest().authenticated();
                })
                // TODO: Verificar se tem necessidade futuramente com os tipos de planos disponíveis.
                // Bloco reservado para personalizar a resposta de acesso não autorizado (JSON em vez da página padrão):
                // .exceptionHandling(handling -> handling
                //         .authenticationEntryPoint((request, response, authException) -> {
                //             response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                //             response.setContentType("application/json");
                //             response.setHeader("WWW-Authenticate", "Bearer");
                //             response.getWriter().write("{\"error\":\"Acesso não autorizado\"}");
                //         })
                // )
                .addFilterBefore(securityFilter, BasicAuthenticationFilter.class);

        return http.build();
    }

}
