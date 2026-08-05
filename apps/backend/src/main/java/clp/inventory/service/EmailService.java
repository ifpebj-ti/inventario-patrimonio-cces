package clp.inventory.service;

// Importa classes do Jakarta Mail (anteriormente JavaMail) para construir mensagens MIME.
import jakarta.mail.internet.MimeMessage;
// Importa anotações do Spring para injeção de dependências e marcação de serviço.
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource; // Para anexar arrays de bytes a e-mails.
import org.springframework.mail.SimpleMailMessage;     // Para mensagens de e-mail simples.
import org.springframework.mail.javamail.JavaMailSender; // Interface principal para envio de e-mail.
import org.springframework.mail.javamail.MimeMessageHelper; // Ajuda a construir MimeMessages complexas (com anexos).
import org.springframework.scheduling.annotation.Async;    // Para executar métodos de forma assíncrona.
import org.springframework.stereotype.Service;             // Anotação para marcar a classe como um serviço.

// Anotação que marca esta classe como um serviço Spring.
// Isso significa que o Spring a gerenciará como um bean e poderá injetá-la em outros componentes.
@Service
public class EmailService {

    // Injeção de dependência do JavaMailSender, que é a interface principal para enviar e-mails no Spring.
    private final JavaMailSender mailSender;
    // Endereço de e-mail do remetente, definido como uma constante.
    private String from = "inventariumclp@gmail.com";

    // Construtor da classe EmailService.
    // O Spring injetará automaticamente uma instância de JavaMailSender.
    @Autowired // A anotação @Autowired no construtor é opcional a partir do Spring 4.3 se houver apenas um construtor.
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender; // Inicializa a dependência do mailSender.
    }

    /**
     * Envia um e-mail de verificação de conta de forma assíncrona.
     *
     * @param to O endereço de e-mail do destinatário.
     * @param token O token de verificação a ser incluído no link.
     */
    @Async // Anotação que faz com que este método seja executado em um thread separado, não bloqueando a execução principal.
    public void sendVerificationEmail(String to, String token) {
        try {
            // Cria uma nova mensagem de e-mail simples.
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from); // Define o remetente.
            message.setTo(to);     // Define o destinatário.
            message.setSubject("Verificação de Email - Inventarium"); // Define o assunto do e-mail.

            // Constrói a URL de verificação usando o token fornecido.
            // O host "http://localhost:3000" sugere que o frontend está rodando localmente nesta porta.
            String url = "http://localhost:3000/auth/verify/" + token;
            // Define o corpo do texto do e-mail, incluindo a URL de verificação e instruções.
            message.setText(
                    "Olá,\n\nObrigado por se  registrar no Inventarium, clique no link abaixo para verificar sua conta\n\n"
                            + url + "\n\nSe você não se registrou, por favor, ignore esse email"
                            + "\n\nAtenciosamente,\nEquipe Inventarium."
            );
            // Envia a mensagem de e-mail.
            mailSender.send(message);
        } catch (Exception e) {
            // Em caso de erro no envio do e-mail, imprime a mensagem de erro e o stack trace.
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Envia um e-mail de redefinição de senha de forma assíncrona.
     *
     * @param to O endereço de e-mail do destinatário.
     * @param token O token de redefinição de senha a ser incluído no link.
     */
    @Async // Anotação que faz com que este método seja executado em um thread separado.
    public void sendPasswordResetEmail(String to, String token) {
        try {
            // Cria uma nova mensagem de e-mail simples.
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from); // Define o remetente.
            message.setTo(to);     // Define o destinatário.
            message.setSubject("Troca de senha - Inventarium"); // Define o assunto do e-mail.
            // Constrói a URL de redefinição de senha usando o token fornecido.
            String url = "http://localhost:3000/auth/reset-password/" + token;
            // Define o corpo do texto do e-mail, incluindo a URL de redefinição e instruções.
            message.setText(
                    "Olá,\n\nEsqueceu sua senha?\nNós recebemos uma solicição de troca de senha para sua conta." +
                            "\n\nPara trocar a senha clique no link abaixo.\n" + url +
                            "\n\nSe você não solicitou, simplesmente ignore esse email." + "\n\nAtenciosamente,\nEquipe Inventarium."
            );
            // Envia a mensagem de e-mail.
            mailSender.send(message);
        } catch (Exception e) {
            // Em caso de erro no envio do e-mail, imprime a mensagem de erro e o stack trace.
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Envia um e-mail com um anexo de forma assíncrona.
     *
     * @param to O endereço de e-mail do destinatário.
     * @param subject O assunto do e-mail.
     * @param message O corpo do texto do e-mail.
     * @param attachmentName O nome do arquivo do anexo.
     * @param attachmentData O array de bytes contendo os dados do anexo.
     */
    @Async // Anotação que faz com que este método seja executado em um thread separado.
    public void sendEmailWithAttachment(
            String to,
            String subject,
            String message,
            String attachmentName,
            byte[] attachmentData
    ) {
        try {
            // Cria uma nova mensagem MIME (permite anexos e conteúdo mais complexo).
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            // Cria um helper para auxiliar na construção da MimeMessage.
            // O segundo argumento 'true' indica que a mensagem será multipart (permite anexos).
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true); // true = multipart (para anexos)

            helper.setFrom(from);       // Define o remetente.
            helper.setTo(to);           // Define o destinatário.
            helper.setSubject(subject); // Define o assunto.
            helper.setText(message, false); // Define o corpo do texto. 'false' indica que é texto puro (não HTML).

            // Adiciona o anexo à mensagem.
            // attachmentName: O nome do arquivo que aparecerá para o destinatário.
            // new ByteArrayResource(attachmentData): Cria um recurso a partir do array de bytes do anexo.
            helper.addAttachment(
                    attachmentName,
                    new ByteArrayResource(attachmentData)
            );

            // Envia a mensagem MIME.
            mailSender.send(mimeMessage);
        } catch (Exception e) {
            // Em caso de erro no envio do e-mail com anexo, imprime uma mensagem de erro e o stack trace.
            System.err.println("Erro ao enviar e-mail com anexo: " + e.getMessage());
            e.printStackTrace();
        }
    }
}