package clp.inventory.itemaudit;

import clp.inventory.model.User;
import clp.inventory.repository.UserRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sobe a aplicação inteira (Postgres real via Testcontainers, HTTP real via TestRestTemplate).
 * O JWT precisa ter como subject o id de um usuário real (Inventory/Item validam via
 * userService.findUserById), mesmo padrão de InventorySectorE2ETest.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ItemValidationE2ETest {

    private static final String TEST_SECRET = "test-secret-de-pelo-menos-32-bytes-aqui";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void authProperties(DynamicPropertyRegistry registry) {
        registry.add("security.token.secret", () -> TEST_SECRET);
    }

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository userRepository;

    private long createUser(String email) {
        User user = new User();
        user.setName("Test User");
        user.setEmail(email);
        user.setGoogleId("google-" + email);
        return userRepository.save(user).getId();
    }

    private HttpHeaders authHeaders(long userId) {
        Algorithm algorithm = Algorithm.HMAC256(TEST_SECRET);
        String token = JWT.create()
                .withIssuer("inventory")
                .withSubject(String.valueOf(userId))
                .withExpiresAt(Instant.now().plus(Duration.ofHours(1)))
                .sign(algorithm);

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private long createOrganization(long userId, String name) {
        var body = new HashMap<String, Object>();
        body.put("name", name);
        var response = restTemplate.postForEntity(
                "/organizations", new HttpEntity<>(body, authHeaders(userId)), Map.class);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private long createSector(long userId, String name, long organizationId) {
        var body = new HashMap<String, Object>();
        body.put("name", name);
        body.put("organizationId", organizationId);
        var response = restTemplate.postForEntity(
                "/sectors", new HttpEntity<>(body, authHeaders(userId)), Map.class);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private void assignSector(long userId, long targetUserId, long sectorId) {
        restTemplate.exchange(
                "/users/" + targetUserId + "/sector", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("sectorId", sectorId), authHeaders(userId)), Map.class);
    }

    private long createInventory(long userId, String name, long sectorId) {
        var body = new HashMap<String, Object>();
        body.put("name", name);
        body.put("description", "Descricao de teste");
        body.put("sectorId", sectorId);
        var response = restTemplate.postForEntity(
                "/inventory/new-inventory", new HttpEntity<>(body, authHeaders(userId)), Map.class);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private void addItem(long userId, long inventoryId, String code) {
        var body = new HashMap<String, Object>();
        body.put("code", code);
        body.put("name", "Item de teste");
        body.put("description", "Descricao do item");
        body.put("price", 1000);
        body.put("locale", "Sala 1");
        body.put("responsible", "Responsavel");
        restTemplate.postForEntity(
                "/inventory/add-single-item?inventoryId=" + inventoryId,
                new HttpEntity<>(body, authHeaders(userId)), Map.class);
    }

    @SuppressWarnings("unchecked")
    private long findItemId(long userId, long inventoryId, String code) {
        var response = restTemplate.exchange(
                "/inventory/inventory-items?inventoryId=" + inventoryId + "&page=0&pageSize=50",
                HttpMethod.GET, new HttpEntity<>(authHeaders(userId)), List.class);

        List<Map<String, Object>> items = (List<Map<String, Object>>) (List<?>) response.getBody();

        return items.stream()
                .filter(item -> code.equals(item.get("code")))
                .findFirst()
                .map(item -> ((Number) item.get("id")).longValue())
                .orElseThrow();
    }

    private ResponseEntity<Map> validateItem(long userId, long itemId) {
        return restTemplate.exchange(
                "/item/" + itemId + "/validate", HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(userId)), Map.class);
    }

    @Test
    void validateItem_sameSector_recordsValidation() {
        long userId = createUser("valida-mesmo-setor@ifpe.edu.br");
        long organizationId = createOrganization(userId, "Organizacao Validacao Mesmo Setor");
        long sectorId = createSector(userId, "Setor Validacao Mesmo Setor", organizationId);
        assignSector(userId, userId, sectorId);
        long inventoryId = createInventory(userId, "Inventario Validacao Mesmo Setor", sectorId);
        addItem(userId, inventoryId, "ITEM-MESMO-SETOR");
        long itemId = findItemId(userId, inventoryId, "ITEM-MESMO-SETOR");

        var response = validateItem(userId, itemId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("isValid")).isEqualTo(true);
        assertThat(response.getBody().get("validatedAt")).isNotNull();

        var audits = restTemplate.exchange(
                "/item-audits?itemId=" + itemId, HttpMethod.GET, new HttpEntity<>(authHeaders(userId)), List.class);
        assertThat(audits.getBody()).hasSize(1);
        var audit = (Map<?, ?>) audits.getBody().get(0);
        assertThat(audit.get("eventType")).isEqualTo("VALIDATION");
        assertThat(audit.get("status")).isEqualTo("VALIDATED");
    }

    @Test
    void validateItem_differentSector_recordsPartialValidation() {
        long ownerUserId = createUser("dono-inventario@ifpe.edu.br");
        long organizationId = createOrganization(ownerUserId, "Organizacao Validacao Setor Diferente");
        long ownerSectorId = createSector(ownerUserId, "Setor Dono", organizationId);
        long inventoryId = createInventory(ownerUserId, "Inventario Setor Diferente", ownerSectorId);
        addItem(ownerUserId, inventoryId, "ITEM-SETOR-DIFERENTE");
        long itemId = findItemId(ownerUserId, inventoryId, "ITEM-SETOR-DIFERENTE");

        long validatorUserId = createUser("valida-outro-setor@ifpe.edu.br");
        long otherSectorId = createSector(ownerUserId, "Setor Validador", organizationId);
        assignSector(ownerUserId, validatorUserId, otherSectorId);

        var response = validateItem(validatorUserId, itemId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("isValid")).isEqualTo(true);

        var audits = restTemplate.exchange(
                "/item-audits?itemId=" + itemId, HttpMethod.GET, new HttpEntity<>(authHeaders(ownerUserId)), List.class);
        var audit = (Map<?, ?>) audits.getBody().get(0);
        assertThat(audit.get("eventType")).isEqualTo("PARTIAL_VALIDATION");
        assertThat(audit.get("status")).isEqualTo("PENDING_TARGET_SECTOR");
    }

    @Test
    void validateItem_actorWithoutSector_recordsSimpleValidation() {
        long ownerUserId = createUser("dono-sem-setor-ator@ifpe.edu.br");
        long organizationId = createOrganization(ownerUserId, "Organizacao Ator Sem Setor");
        long sectorId = createSector(ownerUserId, "Setor Inventario Ator Sem Setor", organizationId);
        long inventoryId = createInventory(ownerUserId, "Inventario Ator Sem Setor", sectorId);
        addItem(ownerUserId, inventoryId, "ITEM-ATOR-SEM-SETOR");
        long itemId = findItemId(ownerUserId, inventoryId, "ITEM-ATOR-SEM-SETOR");

        long validatorUserId = createUser("validador-sem-setor@ifpe.edu.br");

        var response = validateItem(validatorUserId, itemId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        var audits = restTemplate.exchange(
                "/item-audits?itemId=" + itemId, HttpMethod.GET, new HttpEntity<>(authHeaders(ownerUserId)), List.class);
        var audit = (Map<?, ?>) audits.getBody().get(0);
        assertThat(audit.get("eventType")).isEqualTo("VALIDATION");
        assertThat(audit.get("status")).isEqualTo("VALIDATED");
    }

    @Test
    void validateItem_itemNotFound_returns404() {
        long userId = createUser("valida-item-inexistente@ifpe.edu.br");

        var response = validateItem(userId, 999999L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listItemAudits_filterByItemId_returnsOnlyThatItemAudits() {
        long userId = createUser("listagem-audit-item@ifpe.edu.br");
        long organizationId = createOrganization(userId, "Organizacao Listagem Audit Item");
        long sectorId = createSector(userId, "Setor Listagem Audit Item", organizationId);
        assignSector(userId, userId, sectorId);
        long inventoryId = createInventory(userId, "Inventario Listagem Audit Item", sectorId);
        addItem(userId, inventoryId, "ITEM-LISTAGEM-A");
        addItem(userId, inventoryId, "ITEM-LISTAGEM-B");
        long itemAId = findItemId(userId, inventoryId, "ITEM-LISTAGEM-A");
        long itemBId = findItemId(userId, inventoryId, "ITEM-LISTAGEM-B");

        validateItem(userId, itemAId);
        validateItem(userId, itemBId);

        var response = restTemplate.exchange(
                "/item-audits?itemId=" + itemAId, HttpMethod.GET, new HttpEntity<>(authHeaders(userId)), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void listItemAudits_filterByTargetSectorId_returnsOnlyThoseAudits() {
        long userId = createUser("listagem-audit-setor@ifpe.edu.br");
        long organizationId = createOrganization(userId, "Organizacao Listagem Audit Setor");
        long sectorA = createSector(userId, "Setor Listagem Audit A", organizationId);
        long sectorB = createSector(userId, "Setor Listagem Audit B", organizationId);
        assignSector(userId, userId, sectorA);

        long inventoryA = createInventory(userId, "Inventario Listagem Audit A", sectorA);
        long inventoryB = createInventory(userId, "Inventario Listagem Audit B", sectorB);
        addItem(userId, inventoryA, "ITEM-SETOR-A");
        addItem(userId, inventoryB, "ITEM-SETOR-B");
        long itemAId = findItemId(userId, inventoryA, "ITEM-SETOR-A");
        long itemBId = findItemId(userId, inventoryB, "ITEM-SETOR-B");

        validateItem(userId, itemAId);
        validateItem(userId, itemBId);

        var response = restTemplate.exchange(
                "/item-audits?targetSectorId=" + sectorA, HttpMethod.GET, new HttpEntity<>(authHeaders(userId)), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void unauthenticated_returns403() {
        var validateResponse = restTemplate.exchange(
                "/item/1/validate", HttpMethod.PATCH, HttpEntity.EMPTY, Map.class);
        assertThat(validateResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var listResponse = restTemplate.getForEntity("/item-audits", Map.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
