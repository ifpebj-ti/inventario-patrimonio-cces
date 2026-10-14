package clp.inventory.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import clp.inventory.dto.CreateUserDto;
import clp.inventory.dto.GoogleUserInfo;
import clp.inventory.model.*;
import clp.inventory.repository.*;
import java.util.List;
import java.util.Optional;
import javax.security.sasl.AuthenticationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AdminUserServiceTest {
  private final AuthorizationService auth = mock(AuthorizationService.class);
  private final UserRepository users = mock(UserRepository.class);
  private final ProfileRepository profiles = mock(ProfileRepository.class);
  private final SectorRepository sectors = mock(SectorRepository.class);
  private final SectorAllocationRepository allocations = mock(SectorAllocationRepository.class);
  private final AdminUserService service =
      new AdminUserService(auth, users, profiles, sectors, allocations);
  private final UserService googleUsers =
      new UserService(
          users,
          profiles,
          mock(SectorAllocationRepository.class),
          mock(InventoryRepository.class),
          auth);

  @Test
  void createsPendingUserWithNormalizedEmailProfileAndAllSectors() {
    Profile profile = new Profile("GESTOR_SETOR", "");
    Sector first = new Sector("Primeiro", null);
    Sector second = new Sector("Segundo", null);
    when(auth.isAdmin(7L)).thenReturn(true);
    when(profiles.findById(2L)).thenReturn(Optional.of(profile));
    when(sectors.findAllById(List.of(1L, 3L))).thenReturn(List.of(first, second));
    when(users.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    User saved =
        service.create(
            new CreateUserDto(" Nome ", " Pessoa@IFPE.edu.br ", 2L, List.of(1L, 3L)), 7L);
    assertThat(saved.getEmail()).isEqualTo("pessoa@ifpe.edu.br");
    assertThat(saved.getName()).isEqualTo("Nome");
    assertThat(saved.getProfile()).isSameAs(profile);
    assertThat(saved.isPendingGoogleLogin()).isTrue();
    assertThat(saved.getGoogleId()).isNull();
    assertThat(saved.isSystemUser()).isFalse();
    verify(auth).requireAnyPermission(7L, "ADMIN", "MANAGE_SECTOR");
    verify(auth).requireAssignProfile(7L, profile);
    verify(allocations)
        .saveAll(
            argThat(
                rows -> {
                  var iterator = rows.iterator();
                  return iterator.next().sector() == first
                      && iterator.next().sector() == second
                      && !iterator.hasNext();
                }));
  }

  @Test
  void adminCreatesUserWithoutSectorsAndUsesEmailPrefixAsName() {
    Profile profile = new Profile("CONSULTA", "");
    when(auth.isAdmin(7L)).thenReturn(true);
    when(profiles.findById(4L)).thenReturn(Optional.of(profile));
    when(users.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    when(sectors.findAllById(List.of())).thenReturn(List.of());
    User saved = service.create(new CreateUserDto(null, "pessoa@ifpe.edu.br", 4L, List.of()), 7L);
    assertThat(saved.getName()).isEqualTo("pessoa");
    assertThat(saved.getProfile()).isSameAs(profile);
  }

  @Test
  void requiresProfileBecauseThereIsNoUserWithoutProfile() {
    assertThatThrownBy(
            () ->
                service.create(new CreateUserDto(null, "pessoa@ifpe.edu.br", null, List.of()), 7L))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    verify(users, never()).saveAndFlush(any());
  }

  @Test
  void managerCannotCreateManagersOrAdmins() {
    Profile managerProfile = new Profile("GESTOR_SETOR", "");
    when(profiles.findById(2L)).thenReturn(Optional.of(managerProfile));
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
        .when(auth)
        .requireAssignProfile(7L, managerProfile);
    assertThatThrownBy(
            () ->
                service.create(new CreateUserDto(null, "pessoa@ifpe.edu.br", 2L, List.of(1L)), 7L))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    verify(users, never()).saveAndFlush(any());
  }

  @Test
  void managerMustLinkNewUserOnlyToOwnSectors() {
    Profile operator = new Profile("OPERADOR_CAMPO", "");
    when(profiles.findById(3L)).thenReturn(Optional.of(operator));
    when(auth.isAdmin(7L)).thenReturn(false);
    when(auth.allocatedSectorIds(7L)).thenReturn(java.util.Set.of(1L));
    assertThatThrownBy(
            () ->
                service.create(
                    new CreateUserDto(null, "pessoa@ifpe.edu.br", 3L, List.of(1L, 2L)), 7L))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    assertThatThrownBy(
            () -> service.create(new CreateUserDto(null, "pessoa@ifpe.edu.br", 3L, List.of()), 7L))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    verify(users, never()).saveAndFlush(any());
  }

  @Test
  void managerCreatesOperatorInOwnSector() {
    Profile operator = new Profile("OPERADOR_CAMPO", "");
    Sector own = new Sector("Meu setor", null);
    when(profiles.findById(3L)).thenReturn(Optional.of(operator));
    when(auth.isAdmin(7L)).thenReturn(false);
    when(auth.allocatedSectorIds(7L)).thenReturn(java.util.Set.of(1L));
    when(sectors.findAllById(List.of(1L))).thenReturn(List.of(own));
    when(users.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    User saved = service.create(new CreateUserDto(null, "pessoa@ifpe.edu.br", 3L, List.of(1L)), 7L);
    assertThat(saved.getProfile()).isSameAs(operator);
    verify(auth).requireAssignProfile(7L, operator);
  }

  @Test
  void rejectsNonAdminBeforeLookingUpData() {
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
        .when(auth)
        .requireAnyPermission(7L, "ADMIN", "MANAGE_SECTOR");
    assertThatThrownBy(
            () ->
                service.create(new CreateUserDto(null, "pessoa@ifpe.edu.br", null, List.of()), 7L))
        .isInstanceOf(ResponseStatusException.class);
    verifyNoInteractions(users, profiles, sectors, allocations);
  }

  @Test
  void rejectsDuplicateEmailBeforeSaving() {
    when(users.findByEmailIgnoreCase("pessoa@ifpe.edu.br")).thenReturn(Optional.of(new User()));
    assertThatThrownBy(
            () ->
                service.create(new CreateUserDto(null, "Pessoa@IFPE.edu.br", null, List.of()), 7L))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    verify(users, never()).saveAndFlush(any());
    verifyNoInteractions(allocations);
  }

  @Test
  void rejectsMissingSectorWithoutPartialUserCreation() {
    when(auth.isAdmin(7L)).thenReturn(true);
    when(profiles.findById(4L)).thenReturn(Optional.of(new Profile("CONSULTA", "")));
    when(sectors.findAllById(List.of(1L))).thenReturn(List.of());
    assertThatThrownBy(
            () ->
                service.create(new CreateUserDto(null, "pessoa@ifpe.edu.br", 4L, List.of(1L)), 7L))
        .isInstanceOf(ResponseStatusException.class);
    verify(users, never()).saveAndFlush(any());
    verifyNoInteractions(allocations);
  }

  @Test
  void rejectsInactiveSectorWithoutPartialUserCreation() {
    Sector sector = new Sector("Inativo", null);
    sector.setActive(false);
    when(auth.isAdmin(7L)).thenReturn(true);
    when(profiles.findById(4L)).thenReturn(Optional.of(new Profile("CONSULTA", "")));
    when(sectors.findAllById(List.of(1L))).thenReturn(List.of(sector));
    assertThatThrownBy(
            () ->
                service.create(new CreateUserDto(null, "pessoa@ifpe.edu.br", 4L, List.of(1L)), 7L))
        .isInstanceOf(ResponseStatusException.class);
    verify(users, never()).saveAndFlush(any());
    verifyNoInteractions(allocations);
  }

  @Test
  void rejectsUnavailableProfileWithoutSavingUser() {
    assertThatThrownBy(
            () -> service.create(new CreateUserDto(null, "pessoa@ifpe.edu.br", 99L, List.of()), 7L))
        .isInstanceOf(ResponseStatusException.class);
    verify(users, never()).saveAndFlush(any());
  }

  @Test
  void googleLoginBindsPendingUserAndPreservesIdNameAndProfile() throws Exception {
    User pending = new User();
    pending.setId(42L);
    pending.setName("Nome cadastrado pelo admin");
    pending.setPendingGoogleLogin(true);
    Profile profile = new Profile("GESTOR_SETOR", "");
    pending.setProfile(profile);
    when(users.findByEmailForUpdate("pessoa@ifpe.edu.br")).thenReturn(Optional.of(pending));
    when(users.save(pending)).thenReturn(pending);
    User result =
        googleUsers.findOrCreateGoogleUser(
            new GoogleUserInfo("google-sub", "Pessoa@IFPE.edu.br", "Outro nome"));
    assertThat(result.getId()).isEqualTo(42L);
    assertThat(result.getName()).isEqualTo("Nome cadastrado pelo admin");
    assertThat(result.getProfile()).isSameAs(profile);
    assertThat(result.getGoogleId()).isEqualTo("google-sub");
    assertThat(result.isPendingGoogleLogin()).isFalse();
    verify(users, never()).saveAndFlush(any());
  }

  @Test
  void firstGoogleLoginCreatesAccountWithConsultProfile() throws Exception {
    Profile consult = new Profile("CONSULTA", "");
    when(users.findByEmailForUpdate("novo@ifpe.edu.br")).thenReturn(Optional.empty());
    when(profiles.findByName("CONSULTA")).thenReturn(Optional.of(consult));
    when(users.save(any())).thenAnswer(call -> call.getArgument(0));
    User created =
        googleUsers.findOrCreateGoogleUser(
            new GoogleUserInfo("google-sub", "novo@ifpe.edu.br", "Novo"));
    assertThat(created.getProfile()).isSameAs(consult);
  }

  @Test
  void deactivatedAccountCannotSignInWithGoogle() {
    User user = new User();
    user.setGoogleId("google-sub");
    user.setActive(false);
    when(users.findByEmailForUpdate("pessoa@ifpe.edu.br")).thenReturn(Optional.of(user));
    assertThatThrownBy(
            () ->
                googleUsers.findOrCreateGoogleUser(
                    new GoogleUserInfo("google-sub", "pessoa@ifpe.edu.br", "Nome")))
        .isInstanceOf(AuthenticationException.class);
    verify(users, never()).save(any());
  }

  @Test
  void anotherGoogleIdentityCannotReuseExistingUser() {
    User user = new User();
    user.setGoogleId("original-sub");
    when(users.findByEmailForUpdate("pessoa@ifpe.edu.br")).thenReturn(Optional.of(user));
    assertThatThrownBy(
            () ->
                googleUsers.findOrCreateGoogleUser(
                    new GoogleUserInfo("different-sub", "pessoa@ifpe.edu.br", "Nome")))
        .isInstanceOf(AuthenticationException.class);
    verify(users, never()).save(any());
  }

  @Test
  void systemAccountCannotBindGoogleIdentity() {
    User user = new User();
    user.setSystemUser(true);
    when(users.findByEmailForUpdate("admin")).thenReturn(Optional.of(user));
    assertThatThrownBy(
            () ->
                googleUsers.findOrCreateGoogleUser(
                    new GoogleUserInfo("google-sub", "admin", "Nome")))
        .isInstanceOf(AuthenticationException.class);
    verify(users, never()).save(any());
  }
}
