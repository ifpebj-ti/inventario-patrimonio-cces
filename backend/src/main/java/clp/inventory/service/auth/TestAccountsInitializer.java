package clp.inventory.service.auth;

import clp.inventory.model.Inventory;
import clp.inventory.model.Item;
import clp.inventory.model.Permission;
import clp.inventory.model.Role;
import clp.inventory.model.Sector;
import clp.inventory.model.SectorAllocation;
import clp.inventory.model.User;
import clp.inventory.repository.InventoryRepository;
import clp.inventory.repository.PermissionRepository;
import clp.inventory.repository.RoleRepository;
import clp.inventory.repository.SectorAllocationRepository;
import clp.inventory.repository.SectorRepository;
import clp.inventory.repository.UserRepository;
import clp.inventory.service.AuthorizationService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contas de teste: uma por permissão do catálogo (Consultar, Gerenciar Item e Gerenciar Setor), com
 * login por usuário e senha como a conta de sistema, para quem for testar o sistema. Cada conta é
 * independente e o .env é a fonte da verdade: senha vazia desliga o login daquela conta (e não a
 * cria se ela não existe), senha com menos de 12 caracteres é recusada e senha nova troca o hash.
 *
 * <p>São contas de sistema (único jeito de ter senha, sem Google), mas não administradoras: cada
 * uma tem um cargo de teste no "Setor de Testes", gravado direto no repositório porque a API
 * responde 404 para conta de sistema. O que já existe não é alterado (cargos de teste editados pelo
 * administrador e alocações são respeitados); só se cria o que falta. Roda depois do Liquibase.
 */
@Component
public class TestAccountsInitializer implements ApplicationRunner {

  static final int MIN_PASSWORD_LENGTH = 12;

  public static final String SECTOR_NAME = "Setor de Testes";
  public static final String SECTOR_CODE = "TESTE";
  public static final String INVENTORY_NAME = "Inventário de teste";

  public static final String VIEWER_LOGIN = "teste.consulta";
  public static final String OPERATOR_LOGIN = "teste.operador";
  public static final String MANAGER_LOGIN = "teste.gestor";

  private static final Logger log = LoggerFactory.getLogger(TestAccountsInitializer.class);

  /** Uma conta de teste: login fixo, cargo de teste e a senha configurada para ela. */
  private record Account(
      String login,
      String name,
      String roleName,
      String roleDescription,
      List<String> permissions,
      String passwordVariable,
      String password) {}

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final SectorRepository sectorRepository;
  private final SectorAllocationRepository allocationRepository;
  private final InventoryRepository inventoryRepository;
  private final PasswordEncoder passwordEncoder;
  private final List<Account> accounts;

  public TestAccountsInitializer(
      UserRepository userRepository,
      RoleRepository roleRepository,
      PermissionRepository permissionRepository,
      SectorRepository sectorRepository,
      SectorAllocationRepository allocationRepository,
      InventoryRepository inventoryRepository,
      PasswordEncoder passwordEncoder,
      @Value("${test.accounts.viewer.password:}") String viewerPassword,
      @Value("${test.accounts.operator.password:}") String operatorPassword,
      @Value("${test.accounts.manager.password:}") String managerPassword) {
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
    this.permissionRepository = permissionRepository;
    this.sectorRepository = sectorRepository;
    this.allocationRepository = allocationRepository;
    this.inventoryRepository = inventoryRepository;
    this.passwordEncoder = passwordEncoder;
    this.accounts =
        List.of(
            new Account(
                VIEWER_LOGIN,
                "Conta de teste — Consulta",
                "Teste · Consulta",
                "Conta de teste: só consulta o setor, sem alterar nada.",
                List.of(AuthorizationService.VIEW_SECTOR),
                "TEST_VIEWER_PASSWORD",
                viewerPassword),
            new Account(
                OPERATOR_LOGIN,
                "Conta de teste — Operador",
                "Teste · Operador",
                "Conta de teste: valida itens e registra observações no setor.",
                List.of(AuthorizationService.MANAGE_ITEM, AuthorizationService.VIEW_SECTOR),
                "TEST_OPERATOR_PASSWORD",
                operatorPassword),
            new Account(
                MANAGER_LOGIN,
                "Conta de teste — Gestor",
                "Teste · Gestor",
                "Conta de teste: gerencia os inventários e os usuários do setor.",
                List.of(AuthorizationService.MANAGE_SECTOR, AuthorizationService.VIEW_SECTOR),
                "TEST_MANAGER_PASSWORD",
                managerPassword));
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    List<User> enabled = new ArrayList<>();
    for (Account account : accounts) {
      enable(account).ifPresent(enabled::add);
    }
    if (enabled.isEmpty()) {
      return;
    }
    for (User user : enabled) {
      if (allocationRepository.findByUser_Id(user.getId()).isEmpty()) {
        Account account = accountOf(user);
        allocationRepository.save(new SectorAllocation(user, testSector(), roleOf(account)));
        log.info("Conta de teste '{}' alocada ao '{}'.", account.login(), SECTOR_NAME);
      }
    }
    seedInventoryIfEmpty(enabled);
  }

  /** Liga (ou desliga) uma conta conforme a senha; devolve o usuário só se o login ficou ligado. */
  private Optional<User> enable(Account account) {
    Optional<User> existing = userRepository.findByEmail(account.login());
    if (existing.isPresent() && !existing.get().isSystemUser()) {
      log.error(
          "Já existe um usuário que não é de sistema com o identificador '{}'; a conta de teste"
              + " não foi criada nem alterada.",
          account.login());
      return Optional.empty();
    }
    if (account.password().isEmpty()) {
      if (existing.isPresent() && existing.get().getPasswordHash() != null) {
        existing.get().setPasswordHash(null);
        userRepository.save(existing.get());
      }
      log.info(
          "{} vazio: login da conta de teste '{}' desligado.",
          account.passwordVariable(),
          account.login());
      return Optional.empty();
    }
    if (account.password().length() < MIN_PASSWORD_LENGTH) {
      log.error(
          "{} tem menos de {} caracteres; a conta de teste '{}' não foi criada nem alterada.",
          account.passwordVariable(),
          MIN_PASSWORD_LENGTH,
          account.login());
      return Optional.empty();
    }
    User user = existing.orElseGet(() -> newUser(account));
    if (user.getPasswordHash() == null
        || !passwordEncoder.matches(account.password(), user.getPasswordHash())) {
      user.setPasswordHash(passwordEncoder.encode(account.password()));
    }
    user.setAdministrator(false);
    log.info("Conta de teste '{}' pronta para login por senha.", account.login());
    return Optional.of(userRepository.save(user));
  }

  private User newUser(Account account) {
    User user = new User();
    user.setName(account.name());
    user.setEmail(account.login());
    user.setSystemUser(true);
    return user;
  }

  private Account accountOf(User user) {
    return accounts.stream()
        .filter(account -> account.login().equals(user.getEmail()))
        .findFirst()
        .orElseThrow();
  }

  /**
   * O setor de testes: o das alocações de outras contas de teste, senão o de mesmo nome, senão
   * cria. Assim renomear o setor depois não gera um segundo.
   */
  private Sector testSector() {
    for (Account account : accounts) {
      Optional<Sector> allocated =
          userRepository
              .findByEmail(account.login())
              .flatMap(
                  user -> allocationRepository.findByUser_Id(user.getId()).stream().findFirst())
              .map(SectorAllocation::sector);
      if (allocated.isPresent()) {
        return allocated.get();
      }
    }
    return sectorRepository
        .findByName(SECTOR_NAME)
        .orElseGet(() -> sectorRepository.save(new Sector(SECTOR_NAME, SECTOR_CODE)));
  }

  /** O cargo de teste da conta: o de mesmo nome, senão cria com as permissões dela. */
  private Role roleOf(Account account) {
    return roleRepository
        .findByName(account.roleName())
        .orElseGet(
            () -> {
              Role role = new Role(account.roleName(), account.roleDescription());
              Set<Permission> permissions = new HashSet<>();
              for (String name : account.permissions()) {
                permissions.add(permissionRepository.findByName(name).orElseThrow());
              }
              role.setPermissions(permissions);
              return roleRepository.save(role);
            });
  }

  /** Um inventário de exemplo, para quem testa ter o que consultar, validar e editar. */
  private void seedInventoryIfEmpty(List<User> enabled) {
    Sector sector = testSector();
    if (inventoryRepository.existsBySector_Id(sector.id())) {
      return;
    }
    User owner =
        enabled.stream()
            .filter(user -> MANAGER_LOGIN.equals(user.getEmail()))
            .findFirst()
            .orElse(enabled.get(0));
    Inventory inventory =
        new Inventory(
            INVENTORY_NAME,
            "Inventário de exemplo para testar as contas de teste.",
            LocalDate.now().plusMonths(3),
            owner,
            sector);
    String[][] items = {
      {"Mesa de escritório", "Sala 101", "Maria Souza"},
      {"Cadeira giratória", "Sala 101", "Maria Souza"},
      {"Notebook", "Sala 102", "João Lima"},
      {"Monitor 24 polegadas", "Sala 102", "João Lima"},
      {"Projetor", "Auditório", "Ana Costa"},
      {"Impressora", "Secretaria", "Carlos Dias"},
      {"Armário de aço", "Secretaria", "Carlos Dias"},
      {"Ar-condicionado", "Sala 103", "Ana Costa"},
    };
    for (int i = 0; i < items.length; i++) {
      inventory.addItem(
          new Item(
              String.format(Locale.ROOT, "TESTE-%03d", i + 1),
              items[i][0],
              items[i][0],
              (i + 1) * 15000L,
              items[i][1],
              items[i][2]));
    }
    inventoryRepository.save(inventory);
    log.info("Inventário de exemplo criado no '{}'.", SECTOR_NAME);
  }
}
