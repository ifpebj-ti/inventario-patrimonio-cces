package clp.inventory.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
                .authorizeHttpRequests(auth -> {
                    auth
                            .requestMatchers("/new-user").permitAll()
                            .requestMatchers("/auth/login").permitAll()
                            .requestMatchers("/auth/verify/{token}").permitAll()
                            .requestMatchers("/auth/reset-password").permitAll()
                            .requestMatchers(HttpMethod.PUT, "/auth/reset-password/{token}").permitAll();
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
