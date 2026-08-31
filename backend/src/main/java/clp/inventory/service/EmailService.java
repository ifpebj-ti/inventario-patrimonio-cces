package clp.inventory.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private String from = "inventariumclp@gmail.com";

    @Autowired
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendVerificationEmail(String to, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject("Verificação de Email - Inventarium");

            String url = "http://localhost:3000/auth/verify/" + token;
            message.setText(
                    "Olá,\n\nObrigado por se  registrar no Inventarium, clique no link abaixo para verificar sua conta\n\n"
                            + url + "\n\nSe você não se registrou, por favor, ignore esse email"
                            + "\n\nAtenciosamente,\nEquipe Inventarium."
            );
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }

    @Async
    public void sendPasswordResetEmail(String to, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject("Troca de senha - Inventarium");
            String url = "http://localhost:3000/auth/reset-password/" + token;
            message.setText(
                    "Olá,\n\nEsqueceu sua senha?\nNós recebemos uma solicição de troca de senha para sua conta." +
                            "\n\nPara trocar a senha clique no link abaixo.\n" + url +
                            "\n\nSe você não solicitou, simplesmente ignore esse email." + "\n\nAtenciosamente,\nEquipe Inventarium."
            );
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }

    @Async
    public void sendEmailWithAttachment(
            String to,
            String subject,
            String message,
            String attachmentName,
            byte[] attachmentData
    ) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);

            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(message, false);

            helper.addAttachment(
                    attachmentName,
                    new ByteArrayResource(attachmentData)
            );

            mailSender.send(mimeMessage);
        } catch (Exception e) {
            System.err.println("Erro ao enviar e-mail com anexo: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
