package clp.inventory.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class EmailServiceTest {

  @Test
  void enviaOEmailComOAnexo() {
    JavaMailSender sender = mock(JavaMailSender.class);
    when(sender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());

    new EmailService(sender)
        .sendEmailWithAttachment(
            "destino@ifpe.edu.br", "Assunto", "Mensagem", "patrimonio.xlsx", new byte[] {1, 2, 3});

    verify(sender).send(any(MimeMessage.class));
  }

  @Test
  void falhaNoEnvioPropagaErroSemExporAMensagemInterna() {
    JavaMailSender sender = mock(JavaMailSender.class);
    when(sender.createMimeMessage()).thenThrow(new IllegalStateException("smtp fora do ar"));

    assertThatThrownBy(
            () ->
                new EmailService(sender)
                    .sendEmailWithAttachment(
                        "destino@ifpe.edu.br", "A", "M", "p.xlsx", new byte[] {1}))
        .isInstanceOf(EmailDeliveryException.class)
        .hasMessage("Não foi possível enviar o e-mail.")
        .hasCauseInstanceOf(IllegalStateException.class);

    verify(sender, never()).send(any(MimeMessage.class));
  }
}
