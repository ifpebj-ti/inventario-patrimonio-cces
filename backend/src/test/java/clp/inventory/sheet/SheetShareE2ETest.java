package clp.inventory.sheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import clp.inventory.model.Inventory;
import clp.inventory.model.InventoryAudit;
import clp.inventory.model.Item;
import clp.inventory.model.Sector;
import clp.inventory.model.User;
import clp.inventory.repository.InventoryAuditRepository;
import clp.inventory.repository.InventoryRepository;
import clp.inventory.repository.SectorRepository;
import clp.inventory.service.EmailDeliveryException;
import clp.inventory.service.EmailService;
import clp.inventory.service.InventoryAuditService;
import clp.inventory.testsupport.TestAccess;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Tirar a planilha patrimonial do sistema (FIN-02): exportar e enviar por e-mail exigem
 * administrador ou gestor do setor, o destinatário só pode ser de domínio permitido, há limite de
 * envios por usuário, a resposta reflete o resultado real e cada ato (também os recusados) fica na
 * auditoria. O envio real do SMTP é mockado: ele é coberto em EmailServiceTest.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"email.sheet.max-per-window=2", "email.sheet.allowed-domains=ifpe.edu.br"})
@AutoConfigureTestRestTemplate
class SheetShareE2ETest {

  private static final String TEST_SECRET = "test-secret-de-pelo-menos-32-bytes-aqui";
  private static final String ALLOWED = "destino@ifpe.edu.br";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void authProperties(DynamicPropertyRegistry registry) {
    registry.add("security.token.secret", () -> TEST_SECRET);
  }

  @MockitoBean EmailService emailService;

  @Autowired TestRestTemplate restTemplate;
  @Autowired TestAccess access;
  @Autowired SectorRepository sectorRepository;
  @Autowired InventoryRepository inventoryRepository;
  @Autowired InventoryAuditRepository inventoryAuditRepository;

  private Sector sector() {
    return sectorRepository.save(new Sector("Setor " + System.nanoTime(), null));
  }

  private User user(String role, Sector... allocatedTo) {
    User user = access.user("sheet-" + System.nanoTime() + "@ifpe.edu.br", role);
    for (Sector sector : allocatedTo) {
      access.allocate(user, sector);
    }
    return user;
  }

  private Inventory inventory(User owner, Sector sector) {
    Inventory inventory =
        new Inventory("Inv " + System.nanoTime(), "", LocalDate.now().plusDays(30), owner, sector);
    inventory.addItem(new Item("P-" + System.nanoTime(), "", "Mesa", 1000, null, "Maria"));
    return inventoryRepository.save(inventory);
  }

  private HttpHeaders headers(User user) {
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(
        JWT.create()
            .withIssuer("inventory")
            .withSubject(String.valueOf(user.getId()))
            .withExpiresAt(Instant.now().plus(Duration.ofHours(1)))
            .sign(Algorithm.HMAC256(TEST_SECRET)));
    return headers;
  }

  private ResponseEntity<String> export(User user, Inventory inventory) {
    return restTemplate.exchange(
        "/item/export-sheet?inventoryId=" + inventory.id(),
        HttpMethod.GET,
        new HttpEntity<>(null, headers(user)),
        String.class);
  }

  private ResponseEntity<String> send(User user, Inventory inventory, Map<String, Object> body) {
    return restTemplate.exchange(
        "/item/send-email-sheet?inventoryId=" + inventory.id(),
        HttpMethod.POST,
        new HttpEntity<>(body, headers(user)),
        String.class);
  }

  private static Map<String, Object> mail(String to) {
    Map<String, Object> body = new HashMap<>();
    body.put("email", to);
    body.put("subject", "Planilha");
    body.put("message", "Segue a planilha.");
    return body;
  }

  private List<InventoryAudit> audits(Inventory inventory) {
    return inventoryAuditRepository.findByInventoryIdOrderByIdAsc(inventory.id());
  }

  // ---- permissão ----

  @Test
  void exportSheet_somenteAdministradorEGestorDoSetor() {
    Sector sector = sector();
    User admin = user(TestAccess.ADMIN);
    User manager = user(TestAccess.MANAGER, sector);
    User operator = user(TestAccess.OPERATOR, sector);
    User viewer = user(TestAccess.CONSULT, sector);
    User otherManager = user(TestAccess.MANAGER, sector());
    Inventory inventory = inventory(manager, sector);

    assertThat(export(admin, inventory).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(export(manager, inventory).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(export(operator, inventory).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(export(viewer, inventory).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(export(otherManager, inventory).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    // Só as duas exportações permitidas deixam rastro; as negadas não chegam a acontecer.
    assertThat(audits(inventory))
        .extracting(InventoryAudit::actionType, InventoryAudit::status)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                InventoryAuditService.ACTION_EXPORT_SHEET, InventoryAuditService.STATUS_SUCCESS),
            org.assertj.core.groups.Tuple.tuple(
                InventoryAuditService.ACTION_EXPORT_SHEET, InventoryAuditService.STATUS_SUCCESS));
  }

  @Test
  void sendEmailSheet_consultaEOperadorRecebem403() {
    Sector sector = sector();
    User manager = user(TestAccess.MANAGER, sector);
    User operator = user(TestAccess.OPERATOR, sector);
    User viewer = user(TestAccess.CONSULT, sector);
    Inventory inventory = inventory(manager, sector);

    assertThat(send(viewer, inventory, mail(ALLOWED)).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(send(operator, inventory, mail(ALLOWED)).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    verify(emailService, never()).sendEmailWithAttachment(any(), any(), any(), any(), any());
    assertThat(audits(inventory)).isEmpty();
  }

  @Test
  void sendEmailSheet_gestorDeOutroSetorRecebe403() {
    Sector sector = sector();
    Inventory inventory = inventory(user(TestAccess.MANAGER, sector), sector);
    User otherManager = user(TestAccess.MANAGER, sector());

    assertThat(send(otherManager, inventory, mail(ALLOWED)).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    verify(emailService, never()).sendEmailWithAttachment(any(), any(), any(), any(), any());
  }

  // ---- envio e auditoria ----

  @Test
  void sendEmailSheet_gestorEnviaEAuditaComSetorDoAutor() {
    Sector sector = sector();
    User manager = user(TestAccess.MANAGER, sector);
    Inventory inventory = inventory(manager, sector);

    var response = send(manager, inventory, mail(ALLOWED));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(emailService)
        .sendEmailWithAttachment(
            eq(ALLOWED), eq("Planilha"), eq("Segue a planilha."), eq("patrimonio.xlsx"), any());
    InventoryAudit audit = audits(inventory).get(0);
    assertThat(audit.actionType()).isEqualTo(InventoryAuditService.ACTION_SEND_SHEET_EMAIL);
    assertThat(audit.status()).isEqualTo(InventoryAuditService.STATUS_SUCCESS);
    assertThat(audit.recipient()).isEqualTo(ALLOWED);
    assertThat(audit.actorUser().getId()).isEqualTo(manager.getId());
    assertThat(audit.actorSectorId()).isEqualTo(sector.id());
    assertThat(audit.occurredAt()).isNotNull();
  }

  @Test
  void sendEmailSheet_administradorNaoTemSetorNaAuditoria() {
    Sector sector = sector();
    User admin = user(TestAccess.ADMIN);
    Inventory inventory = inventory(admin, sector);

    assertThat(send(admin, inventory, mail(ALLOWED)).getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(audits(inventory).get(0).actorSectorId()).isNull();
  }

  // ---- validação do destinatário ----

  @Test
  void sendEmailSheet_dominioForaDaListaRetorna400EAudita() {
    Sector sector = sector();
    User manager = user(TestAccess.MANAGER, sector);
    Inventory inventory = inventory(manager, sector);

    for (String to :
        List.of("alvo@gmail.com", "alvo@ifpe.edu.br.evil.com", "alvo@fakeifpe.edu.br")) {
      assertThat(send(manager, inventory, mail(to)).getStatusCode())
          .as(to)
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    verify(emailService, never()).sendEmailWithAttachment(any(), any(), any(), any(), any());
    assertThat(audits(inventory))
        .extracting(InventoryAudit::status)
        .containsOnly(InventoryAuditService.STATUS_REJECTED_DOMAIN)
        .hasSize(3);
  }

  @Test
  void sendEmailSheet_subdominioPermitidoPassa() {
    Sector sector = sector();
    User manager = user(TestAccess.MANAGER, sector);
    Inventory inventory = inventory(manager, sector);

    assertThat(send(manager, inventory, mail("aluno@discente.ifpe.edu.br")).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void sendEmailSheet_corpoInvalidoRetorna400() {
    Sector sector = sector();
    User manager = user(TestAccess.MANAGER, sector);
    Inventory inventory = inventory(manager, sector);

    Map<String, Object> noEmail = mail(null);
    Map<String, Object> badEmail = mail("nao-e-email");
    Map<String, Object> blankSubject = mail(ALLOWED);
    blankSubject.put("subject", " ");
    Map<String, Object> lineBreak = mail(ALLOWED);
    lineBreak.put("subject", "Assunto\r\nBcc: alvo@ifpe.edu.br");
    Map<String, Object> longSubject = mail(ALLOWED);
    longSubject.put("subject", "a".repeat(201));

    for (Map<String, Object> body :
        List.of(noEmail, badEmail, blankSubject, lineBreak, longSubject)) {
      assertThat(send(manager, inventory, body).getStatusCode())
          .as(body.toString())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
    verify(emailService, never()).sendEmailWithAttachment(any(), any(), any(), any(), any());
  }

  // ---- limite de envios e resultado real ----

  @Test
  void sendEmailSheet_estourarOLimiteRetorna429ComRetryAfter() {
    Sector sector = sector();
    User manager = user(TestAccess.MANAGER, sector);
    Inventory inventory = inventory(manager, sector);

    assertThat(send(manager, inventory, mail(ALLOWED)).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(send(manager, inventory, mail(ALLOWED)).getStatusCode()).isEqualTo(HttpStatus.OK);
    var limited = send(manager, inventory, mail(ALLOWED));

    assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(Long.parseLong(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))).isPositive();
    assertThat(audits(inventory))
        .extracting(InventoryAudit::status)
        .containsExactly(
            InventoryAuditService.STATUS_SUCCESS,
            InventoryAuditService.STATUS_SUCCESS,
            InventoryAuditService.STATUS_RATE_LIMITED);
  }

  @Test
  void sendEmailSheet_falhaDoSmtpRetorna502EAudita() {
    Sector sector = sector();
    User manager = user(TestAccess.MANAGER, sector);
    Inventory inventory = inventory(manager, sector);
    doThrow(new EmailDeliveryException(new IllegalStateException("smtp fora do ar")))
        .when(emailService)
        .sendEmailWithAttachment(anyString(), anyString(), any(), anyString(), any());

    var response = send(manager, inventory, mail(ALLOWED));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(response.getBody()).doesNotContain("smtp fora do ar");
    assertThat(audits(inventory))
        .extracting(InventoryAudit::status)
        .containsExactly(InventoryAuditService.STATUS_FAILED);
  }
}
