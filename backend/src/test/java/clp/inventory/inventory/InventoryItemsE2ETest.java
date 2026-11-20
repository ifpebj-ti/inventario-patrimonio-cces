package clp.inventory.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import clp.inventory.service.EmailService;
import clp.inventory.testsupport.TestAccess;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.nio.charset.StandardCharsets;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sobe a aplicação inteira (Postgres real via Testcontainers) para cobrir a importação de itens por
 * CSV, a validação da planilha, a remoção de itens, o CRUD de inventário e a geração de PDF e envio
 * de planilha por e-mail.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class InventoryItemsE2ETest {

  private static final String TEST_SECRET = "test-secret-de-pelo-menos-32-bytes-aqui";
  private static final String HEADER = "Código;Descrição;Responsável;Valor;Sala;Observação\n";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void authProperties(DynamicPropertyRegistry registry) {
    registry.add("security.token.secret", () -> TEST_SECRET);
  }

  @Autowired TestRestTemplate restTemplate;
  @Autowired TestAccess access;

  // O envio real é coberto em EmailServiceTest; aqui só se confere que o endpoint o aciona.
  @MockitoBean EmailService emailService;

  private HttpHeaders authHeaders(long userId) {
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(
        JWT.create()
            .withIssuer("inventory")
            .withSubject(String.valueOf(userId))
            .withExpiresAt(Instant.now().plus(Duration.ofHours(1)))
            .sign(Algorithm.HMAC256(TEST_SECRET)));
    return headers;
  }

  private long adminId() {
    return access.user("admin-" + System.nanoTime() + "@ifpe.edu.br", TestAccess.ADMIN).getId();
  }

  private long createSector(long adminId) {
    var body = new HashMap<String, Object>();
    body.put("name", "Setor Itens " + System.nanoTime());
    var response =
        restTemplate.postForEntity(
            "/sectors", new HttpEntity<>(body, authHeaders(adminId)), Map.class);
    return ((Number) response.getBody().get("id")).longValue();
  }

  private long createInventory(long adminId, long sectorId) {
    var body = new HashMap<String, Object>();
    body.put("name", "Inventario " + System.nanoTime());
    body.put("description", "Descricao de teste");
    body.put("plannedEndDate", LocalDate.now().plusDays(30).toString());
    body.put("sectorId", sectorId);
    var response =
        restTemplate.postForEntity(
            "/inventory/new-inventory", new HttpEntity<>(body, authHeaders(adminId)), Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return ((Number) response.getBody().get("id")).longValue();
  }

  /**
   * Cria setor e inventário prontos para receber itens. Devolve {adminId, sectorId, inventoryId}.
   */
  private long[] setup() {
    long admin = adminId();
    long sector = createSector(admin);
    return new long[] {admin, sector, createInventory(admin, sector)};
  }

  private ResponseEntity<Map> postCsv(String url, long userId, String filename, String content) {
    var headers = authHeaders(userId);
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    var file =
        new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
          @Override
          public String getFilename() {
            return filename;
          }
        };
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", file);
    return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
  }

  private ResponseEntity<Map> importCsv(long userId, long inventoryId, String content) {
    return postCsv("/inventory/add-items?inventoryId=" + inventoryId, userId, "itens.csv", content);
  }

  private ResponseEntity<List> listItems(long userId, long inventoryId) {
    return restTemplate.exchange(
        "/inventory/inventory-items?inventoryId=" + inventoryId + "&page=0&pageSize=50",
        HttpMethod.GET,
        new HttpEntity<>(authHeaders(userId)),
        List.class);
  }

  private ResponseEntity<Void> delete(long userId, String url) {
    return restTemplate.exchange(
        url, HttpMethod.DELETE, new HttpEntity<>(authHeaders(userId)), Void.class);
  }

  // ---------- importação de CSV ----------

  @Test
  void importaCsvComPontoEVirgulaPrecosEObservacao() {
    long[] s = setup();
    String csv =
        HEADER
            + "TOMB-1;Notebook \"Dell\";Maria;R$ 1.234,56;Sala A;Em bom estado\n"
            + "TOMB-2;\"Mesa; grande\";João;10,50;Sala A;\n"
            + "\n"
            + "TOMB-3;Cadeira;Ana;1500;Sala B;\n";

    var response = importCsv(s[0], s[2], csv);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().get("successes\n")).isEqualTo(3);
    assertThat(((Number) response.getBody().get("createdRooms")).longValue()).isEqualTo(2);
    assertThat(listItems(s[0], s[2]).getBody()).hasSize(3);
    // R$ 1.234,56 (com milhar), 10,50 e 1500 chegam como os mesmos centavos em qualquer caminho.
    assertThat(listItems(s[0], s[2]).getBody().toString())
        .contains("1.234,56")
        .contains("10,50")
        .contains("1.500,00");
  }

  @Test
  void importaCsvComVirgulaEBom() {
    long[] s = setup();
    String csv = "﻿patrimônio,descrição,valor\nV-1,Projetor,\"2.000,00\"\nV-2,Tela,150.5\n";

    var response = importCsv(s[0], s[2], csv);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().get("totalProcessed\n")).isEqualTo(2);
  }

  @Test
  void importaCsvComTabulacao() {
    long[] s = setup();
    String csv = "etiqueta\tdetentor\tvalor\nT-1\tCarlos\t10,00\n";

    var response = importCsv(s[0], s[2], csv);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void importaCsvComCodigoRepetidoRecusaAPlanilhaInteiraENadaEImportado() {
    long[] s = setup();
    String csv =
        HEADER
            + "OK-1;Item;Eu;1,00;Sala A;\nDUP-1;Item;Eu;1,00;Sala A;\nDUP-1;Item de novo;Eu;1,00;Sala A;\n";

    var response = importCsv(s[0], s[2], csv);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("successes\n")).isEqualTo(0);
    assertThat(response.getBody().get("failures\n")).isEqualTo(1);
    assertThat(response.getBody().get("failedItems\n").toString())
        .contains("DUP-1")
        .contains("Código repetido na planilha")
        .contains("linha 3");
    // Nada entrou, nem a linha válida: a importação é tudo ou nada.
    assertThat(listItems(s[0], s[2]).getBody()).isEmpty();
  }

  @Test
  void importaCsvComLinhaInvalidaNaoImportaNenhumItem() {
    long[] s = setup();
    String csv =
        HEADER + "OK-1;Item;Eu;1,00;Sala A;\n" + "C".repeat(31) + ";Item;Eu;1,00;Sala A;\n";

    var response = importCsv(s[0], s[2], csv);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("successes\n")).isEqualTo(0);
    assertThat(listItems(s[0], s[2]).getBody()).isEmpty();
  }

  @Test
  void importaCsvComNomeDeSalaLongoDemaisRecusaAPlanilha() {
    long[] s = setup();
    String csv = HEADER + "OK-1;Item;Eu;1,00;" + "S".repeat(101) + ";\n";

    var response = importCsv(s[0], s[2], csv);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("failedItems\n").toString()).contains("Nome da sala");
    assertThat(listItems(s[0], s[2]).getBody()).isEmpty();
  }

  @Test
  void segundaImportacaoNoMesmoInventarioDevolve409ESemDuplicar() {
    long[] s = setup();
    String csv = HEADER + "SEQ-1;Item;Eu;1,00;Sala A;\nSEQ-2;Item;Eu;1,00;Sala A;\n";

    assertThat(importCsv(s[0], s[2], csv).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    var second = importCsv(s[0], s[2], csv);

    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(listItems(s[0], s[2]).getBody()).hasSize(2);
  }

  @Test
  void validaPlanilhaComCodigoRepetidoDevolveErroNaLinhaRepetida() {
    long[] s = setup();
    String csv = HEADER + "DUP-1;Item;Eu;1,00;Sala;\nDUP-1;Item de novo;Eu;1,00;Sala;\n";

    var response = postCsv("/inventory/validate-sheet", s[0], "itens.csv", csv);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("errors").toString())
        .contains("Código repetido na planilha")
        .contains("linha 2");
  }

  @Test
  void importaCsvComValorAusenteOuMalformadoRecusaAPlanilhaENadaEImportado() {
    long[] s = setup();
    String csv =
        HEADER
            + "OK-1;Item;Eu;1,00;Sala A;\n"
            + "BAD-1;Item;Eu;abc;Sala A;\n"
            + "BAD-2;Item;Eu;;Sala A;\n"
            + "BAD-3;Item;Eu;1,500.00;Sala A;\n"
            + "BAD-4;Item;Eu;-10;Sala A;\n";

    var response = importCsv(s[0], s[2], csv);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("successes\n")).isEqualTo(0);
    assertThat(response.getBody().get("failures\n")).isEqualTo(4);
    String failed = response.getBody().get("failedItems\n").toString();
    assertThat(failed)
        .contains("BAD-1")
        .contains("BAD-2")
        .contains("BAD-3")
        .contains("BAD-4")
        .contains("Informe o valor")
        .contains("Valor inválido");
    // Nenhum valor virou 0 e nem a linha válida entrou.
    assertThat(listItems(s[0], s[2]).getBody()).isEmpty();
  }

  @Test
  void validaPlanilhaComValorMalformadoDevolveErroNaLinha() {
    long[] s = setup();
    String csv = HEADER + "OK-1;Item;Eu;1,00;Sala;\nBAD-1;Item;Eu;12abc;Sala;\n";

    var response = postCsv("/inventory/validate-sheet", s[0], "itens.csv", csv);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("errors").toString())
        .contains("BAD-1")
        .contains("Valor inválido");
  }

  @Test
  void importaCsvRejeitaArquivoVazioFormatoErradoESemColunaDeCodigo() {
    long[] s = setup();

    assertThat(importCsv(s[0], s[2], "").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            postCsv("/inventory/add-items?inventoryId=" + s[2], s[0], "itens.txt", HEADER)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(importCsv(s[0], s[2], "Descrição;Valor\nAlgo;1\n").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(importCsv(s[0], s[2], HEADER).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(importCsv(s[0], s[2], "   \n").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void importaCsvSemPermissaoDevolve403() {
    long[] s = setup();
    long outsider =
        access.user("fora-" + System.nanoTime() + "@ifpe.edu.br", TestAccess.OPERATOR).getId();

    assertThat(importCsv(outsider, s[2], HEADER + "X-1;Item;Eu;1;Sala;\n").getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  // ---------- validação da planilha ----------

  @Test
  void validaPlanilhaValidaComEPrevisaoDeSalas() {
    long[] s = setup();
    String csv = HEADER + "VAL-1;Item;Eu;1,00;Sala Nova;\n";

    var semInventario = postCsv("/inventory/validate-sheet", s[0], "itens.csv", csv);
    var comInventario =
        postCsv("/inventory/validate-sheet?inventoryId=" + s[2], s[0], "itens.csv", csv);

    assertThat(semInventario.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(comInventario.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(comInventario.getBody()).containsKeys("newRooms", "existingRooms");
  }

  @Test
  void validaPlanilhaComCodigoGrandeDemaisDevolveErrosPorLinha() {
    long[] s = setup();
    String csv = HEADER + "C".repeat(31) + ";Item;Eu;1,00;Sala;\n";

    var response = postCsv("/inventory/validate-sheet", s[0], "itens.csv", csv);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsKey("errors");
  }

  @Test
  void validaPlanilhaRejeitaArquivoVazioFormatoErradoEPlanilhaSemItens() {
    long[] s = setup();

    assertThat(postCsv("/inventory/validate-sheet", s[0], "itens.csv", "").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(postCsv("/inventory/validate-sheet", s[0], "itens.xlsx", HEADER).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(postCsv("/inventory/validate-sheet", s[0], "itens.csv", HEADER).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  // ---------- remoção de itens ----------

  @Test
  void removeUmItemELimpaOInventario() {
    long[] s = setup();
    importCsv(s[0], s[2], HEADER + "R-1;Item 1;Eu;1,00;Sala;\nR-2;Item 2;Eu;2,00;Sala;\n");
    List<?> itens = listItems(s[0], s[2]).getBody();
    long itemId = ((Number) ((Map<?, ?>) itens.get(0)).get("id")).longValue();

    assertThat(
            delete(s[0], "/inventory/delete-item?inventoryId=" + s[2] + "&itemId=" + itemId)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(
            delete(s[0], "/inventory/delete-item?inventoryId=" + s[2] + "&itemId=" + itemId)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(listItems(s[0], s[2]).getBody()).hasSize(1);

    assertThat(delete(s[0], "/inventory/clear-items?inventoryId=" + s[2]).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(listItems(s[0], s[2]).getBody()).isEmpty();
  }

  @Test
  void removerItemEmInventarioInexistenteDevolve404() {
    long admin = adminId();

    assertThat(delete(admin, "/inventory/delete-item?inventoryId=999999&itemId=1").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(delete(admin, "/inventory/clear-items?inventoryId=999999").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  // ---------- item avulso e CRUD de inventário ----------

  @Test
  void adicionaItemAvulsoEBuscaOInventario() {
    long[] s = setup();
    var item = new HashMap<String, Object>();
    item.put("code", "AV-" + System.nanoTime());
    item.put("name", "Impressora");
    item.put("description", "Impressora laser");
    item.put("price", "500,00");
    item.put("locale", "Sala Avulsa");

    var created =
        restTemplate.exchange(
            "/inventory/add-single-item?inventoryId=" + s[2],
            HttpMethod.POST,
            new HttpEntity<>(item, authHeaders(s[0])),
            Map.class);
    var details =
        restTemplate.exchange(
            "/inventory/" + s[2], HttpMethod.GET, new HttpEntity<>(authHeaders(s[0])), Map.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(details.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void itemAvulsoComCodigoRepetidoNoInventarioDevolve400() {
    long[] s = setup();
    var item = new HashMap<String, Object>();
    item.put("code", "AV-DUP-" + System.nanoTime());
    item.put("name", "Impressora");
    item.put("price", "500,00");

    var first =
        restTemplate.exchange(
            "/inventory/add-single-item?inventoryId=" + s[2],
            HttpMethod.POST,
            new HttpEntity<>(item, authHeaders(s[0])),
            Map.class);
    var second =
        restTemplate.exchange(
            "/inventory/add-single-item?inventoryId=" + s[2],
            HttpMethod.POST,
            new HttpEntity<>(item, authHeaders(s[0])),
            String.class);

    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(second.getBody()).contains("Já existe um item com o código");
    assertThat(listItems(s[0], s[2]).getBody()).hasSize(1);
  }

  @Test
  void itemAvulsoRecusaValorAusenteOuMalformadoESalvaOValorEmReais() {
    long[] s = setup();
    String url = "/inventory/add-single-item?inventoryId=" + s[2];

    for (String bad : new String[] {null, "", "abc", "-5", "1,500.00", "10,999"}) {
      var item = new HashMap<String, Object>();
      item.put("code", "VAL-" + System.nanoTime());
      item.put("name", "Mesa");
      if (bad != null) {
        item.put("price", bad);
      }
      var response =
          restTemplate.exchange(
              url, HttpMethod.POST, new HttpEntity<>(item, authHeaders(s[0])), String.class);
      assertThat(response.getStatusCode()).as("valor %s", bad).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    assertThat(listItems(s[0], s[2]).getBody()).isEmpty();

    var ok = new HashMap<String, Object>();
    ok.put("code", "VAL-OK");
    ok.put("name", "Mesa");
    ok.put("price", "R$ 1.500,00");
    var created =
        restTemplate.exchange(
            url, HttpMethod.POST, new HttpEntity<>(ok, authHeaders(s[0])), Map.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().get("price").toString()).contains("1.500,00");
  }

  @Test
  void atualizaListaEApagaOInventario() {
    long[] s = setup();
    var update = new HashMap<String, Object>();
    update.put("name", "Inventario renomeado");
    update.put("description", "Nova descricao");
    update.put("plannedEndDate", LocalDate.now().plusDays(60).toString());
    update.put("sectorId", s[1]);

    var updated =
        restTemplate.exchange(
            "/inventory/" + s[2],
            HttpMethod.PUT,
            new HttpEntity<>(update, authHeaders(s[0])),
            Map.class);
    var mine =
        restTemplate.exchange(
            "/inventory/user-inventories",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(s[0])),
            List.class);
    var deleted = delete(s[0], "/inventory/" + s[2]);
    var gone =
        restTemplate.exchange(
            "/inventory/" + s[2], HttpMethod.GET, new HttpEntity<>(authHeaders(s[0])), Map.class);

    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(mine.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(deleted.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void inventarioInexistenteDevolve400NaAtualizacaoE404NaExclusao() {
    long admin = adminId();
    var update = new HashMap<String, Object>();
    update.put("name", "X");
    update.put("description", "Y");
    update.put("plannedEndDate", LocalDate.now().plusDays(10).toString());
    update.put("sectorId", 1);

    var put =
        restTemplate.exchange(
            "/inventory/999999",
            HttpMethod.PUT,
            new HttpEntity<>(update, authHeaders(admin)),
            Map.class);

    // Hoje o PUT de um inventário que não existe responde 400 (o service lança RuntimeException).
    assertThat(put.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(delete(admin, "/inventory/999999").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  // ---------- PDF e e-mail ----------

  @Test
  void geraPdfDeEtiquetasPorIdETodosOsItens() {
    long[] s = setup();
    importCsv(s[0], s[2], HEADER + "P-1;Item 1;Eu;1,00;Sala;\nP-2;Item 2;Eu;2,00;Sala;\n");
    List<?> itens = listItems(s[0], s[2]).getBody();
    List<Long> ids =
        itens.stream().map(i -> ((Number) ((Map<?, ?>) i).get("id")).longValue()).toList();

    var porId =
        restTemplate.exchange(
            "/item/pdf", HttpMethod.POST, new HttpEntity<>(ids, authHeaders(s[0])), byte[].class);
    var todos =
        restTemplate.exchange(
            "/item/all-items-pdf",
            HttpMethod.POST,
            new HttpEntity<>(s[2], authHeaders(s[0])),
            byte[].class);

    assertThat(porId.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(porId.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(todos.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(todos.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
  }

  @Test
  void pdfSemItensDevolve500() {
    long[] s = setup();

    var todos =
        restTemplate.exchange(
            "/item/all-items-pdf",
            HttpMethod.POST,
            new HttpEntity<>(s[2], authHeaders(s[0])),
            byte[].class);

    assertThat(todos.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  void enviaAPlanilhaPorEmail() {
    long[] s = setup();
    importCsv(s[0], s[2], HEADER + "M-1;Item 1;Eu;1,00;Sala;\n");
    var email = new HashMap<String, Object>();
    email.put("email", "destino@ifpe.edu.br");
    email.put("subject", "Planilha");
    email.put("message", "Segue a planilha.");

    var response =
        restTemplate.exchange(
            "/item/send-email-sheet?inventoryId=" + s[2],
            HttpMethod.POST,
            new HttpEntity<>(email, authHeaders(s[0])),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(emailService)
        .sendEmailWithAttachment(
            eq("destino@ifpe.edu.br"),
            eq("Planilha"),
            eq("Segue a planilha."),
            eq("patrimonio.xlsx"),
            any(byte[].class));
  }
}
