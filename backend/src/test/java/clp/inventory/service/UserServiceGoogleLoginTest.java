package clp.inventory.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import clp.inventory.dto.GoogleUserInfo;
import clp.inventory.model.*;
import clp.inventory.repository.*;
import java.util.Optional;
import javax.security.sasl.AuthenticationException;
import org.junit.jupiter.api.Test;

/**
 * O login pelo Google é a única forma de um usuário nascer: sem perfil, até que um administrador
 * atribua o acesso. Não há cadastro nem pré-cadastro de usuário.
 */
class UserServiceGoogleLoginTest {
  private final AuthorizationService auth = mock(AuthorizationService.class);
  private final UserRepository users = mock(UserRepository.class);
  private final ProfileRepository profiles = mock(ProfileRepository.class);
  private final UserService googleUsers =
      new UserService(users, profiles, mock(SectorAllocationRepository.class), auth);

  @Test
  void firstGoogleLoginCreatesAccountWithoutProfile() throws Exception {
    when(users.findByEmailForUpdate("novo@ifpe.edu.br")).thenReturn(Optional.empty());
    when(users.save(any())).thenAnswer(call -> call.getArgument(0));

    User created =
        googleUsers.findOrCreateGoogleUser(
            new GoogleUserInfo("google-sub", "Novo@IFPE.edu.br", "Novo"));

    assertThat(created.getProfile()).isNull();
    assertThat(created.getEmail()).isEqualTo("novo@ifpe.edu.br");
    assertThat(created.getGoogleId()).isEqualTo("google-sub");
    assertThat(created.isActive()).isTrue();
    assertThat(created.isSystemUser()).isFalse();
    verify(profiles, never()).findByName(any());
  }

  @Test
  void existingGoogleAccountSignsInWithoutChanges() throws Exception {
    User user = new User();
    user.setGoogleId("google-sub");
    when(users.findByEmailForUpdate("pessoa@ifpe.edu.br")).thenReturn(Optional.of(user));

    User result =
        googleUsers.findOrCreateGoogleUser(
            new GoogleUserInfo("google-sub", "pessoa@ifpe.edu.br", "Nome"));

    assertThat(result).isSameAs(user);
    verify(users, never()).save(any());
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
