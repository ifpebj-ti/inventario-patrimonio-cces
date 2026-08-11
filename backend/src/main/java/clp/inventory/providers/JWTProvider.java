package clp.inventory.providers;

// Importa classes do Auth0 JWT para manipulação de JSON Web Tokens.
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
// Importa anotações do Spring para injeção de valores de propriedades e para marcar a classe como um serviço.
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// Anotação que marca esta classe como um serviço Spring.
// Isso significa que o Spring a gerenciará como um bean e poderá injetá-la em outros componentes.
@Service
public class JWTProvider {

    // Anotação @Value é usada para injetar o valor da propriedade "security.token.secret"
    // (definida em um arquivo de configuração como application.properties ou .yml)
    // na variável 'secretKey'. Esta chave secreta é essencial para assinar e verificar JWTs.
    @Value("${security.token.secret}")
    private String secretKey; // A chave secreta usada para criptografar/descriptografar o token.

    /**
     * Valida um token JWT e extrai seu 'subject' (geralmente o ID do usuário).
     *
     * @param token O token JWT a ser validado, que pode incluir o prefixo "Bearer ".
     * @return O 'subject' do token se a validação for bem-sucedida; uma string vazia caso contrário.
     */
    public String validateToken(String token) {
        // Remove o prefixo "Bearer " do token, se presente, para obter apenas a parte do token em si.
        token = token.replace("Bearer ", "");
        // Cria um algoritmo HMAC256 usando a chave secreta. Este algoritmo é usado para verificar a assinatura do token.
        Algorithm algorithm = Algorithm.HMAC256(secretKey);

        try {
            // Tenta verificar o token usando o algoritmo e constrói um JWTVerifier.
            // O método .verify(token) decodifica e verifica a assinatura e a validade do token.
            // .getSubject() extrai o "assunto" do token, que geralmente contém o identificador do usuário.
            var subject = JWT.require(algorithm)
                    .build() // Constrói o verificador de JWT.
                    .verify(token) // Tenta verificar o token fornecido.
                    .getSubject(); // Obtém o "subject" do token (o principal que o token representa).

            // Se a verificação for bem-sucedida, retorna o 'subject'.
            return subject;
        } catch (JWTVerificationException e) {
            // Captura qualquer exceção relacionada à verificação do JWT (por exemplo, token inválido, expirado, assinatura incorreta).
            e.printStackTrace(); // Imprime o stack trace da exceção para fins de depuração.
            return ""; // Retorna uma string vazia para indicar que a validação falhou.
        }
    }
}