package clp.inventory.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
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
