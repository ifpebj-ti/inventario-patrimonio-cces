package clp.inventory.utils;

// Importa classes do Auth0 JWT para manipulação de JSON Web Tokens (JWT).
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
// Importa anotações do Spring para injeção de valores de propriedades e para marcar a classe como um componente.
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Anotação que marca esta classe como um componente Spring.
// Isso significa que o Spring a gerenciará como um bean e poderá injetá-la em outros componentes.
@Component
public class AuthenticationUtils {

    // Anotação @Value é usada para injetar o valor da propriedade "security.token.secret"
    // (definida em um arquivo de configuração como application.properties ou .yml)
    // na variável 'secretKey'. Esta chave secreta é essencial para verificar a assinatura de JWTs.
    @Value("${security.token.secret}")
    private String secretKey; // A chave secreta usada para verificar o token.

    // Construtor padrão. É necessário para que o Spring possa instanciar este componente.
    public AuthenticationUtils() {}

    /**
     * Extrai o ID do usuário (subject) de um token JWT.
     * Este método assume que o token é válido e já foi assinado com a chave secreta correta.
     *
     * @param token O token JWT a ser decodificado, que pode incluir o prefixo "Bearer ".
     * @return O 'subject' do token, que geralmente é o ID do usuário.
     * @throws com.auth0.jwt.exceptions.JWTVerificationException Se o token for inválido, expirado ou tiver uma assinatura incorreta.
     */
    public String getUserIdFromToken(String token) {
        // Remove o prefixo "Bearer " do token, se presente, para obter apenas a parte do token em si.
        token = token.replace("Bearer ", "");
        // Cria um algoritmo HMAC256 usando a chave secreta. Este algoritmo é usado para verificar a assinatura do token.
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        // Constrói um JWTVerifier (verificador de JWT):
        // - JWT.require(algorithm): Especifica o algoritmo a ser usado para verificar a assinatura.
        // - .withIssuer("inventory"): Espera que o emissor do token seja "inventory".
        // - .build(): Conclui a construção do verificador.
        // - .verify(token): Tenta verificar e decodificar o token fornecido.
        // - .getSubject(): Extrai o 'subject' (assunto) do token, que neste contexto é o ID do usuário.
        return JWT.require(algorithm).withIssuer("inventory").build().verify(token).getSubject();
    }
}