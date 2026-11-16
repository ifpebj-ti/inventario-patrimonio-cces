package clp.inventory.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

  private static final Logger log = LoggerFactory.getLogger(EmailService.class);

  private final JavaMailSender mailSender;
  private String from = "inventariumclp@gmail.com";

  @Autowired
  public EmailService(JavaMailSender mailSender) {
    this.mailSender = mailSender;
  }

  /**
   * Envia o e-mail com o anexo, de forma síncrona: só retorna se o servidor SMTP aceitou a
   * mensagem. Qualquer falha vira {@link EmailDeliveryException} (a causa vai para o log), para o
   * chamador responder o resultado real em vez de um sucesso que não aconteceu.
   */
  public void sendEmailWithAttachment(
      String to, String subject, String message, String attachmentName, byte[] attachmentData) {
    try {
      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);

      helper.setFrom(from);
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(message, false);

      helper.addAttachment(attachmentName, new ByteArrayResource(attachmentData));

      mailSender.send(mimeMessage);
    } catch (Exception e) {
      log.error("Erro ao enviar e-mail com anexo", e);
      throw new EmailDeliveryException(e);
    }
  }
}
