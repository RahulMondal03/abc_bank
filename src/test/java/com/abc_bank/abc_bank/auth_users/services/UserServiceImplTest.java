package com.abc_bank.abc_bank.auth_users.services;

import com.abc_bank.abc_bank.auth_users.dtos.LoginRequest;
import com.abc_bank.abc_bank.auth_users.dtos.LoginResponse;
import com.abc_bank.abc_bank.auth_users.dtos.RegistrationRequest;
import com.abc_bank.abc_bank.auth_users.dtos.ResetPasswordRequest;
import com.abc_bank.abc_bank.auth_users.dtos.UpdatePasswordRequest;
import com.abc_bank.abc_bank.auth_users.dtos.UserDTO;
import com.abc_bank.abc_bank.auth_users.entity.PasswordResetCode;
import com.abc_bank.abc_bank.auth_users.entity.User;
import com.abc_bank.abc_bank.auth_users.repo.PasswordResetCodeRepo;
import com.abc_bank.abc_bank.auth_users.repo.UserRepo;
import com.abc_bank.abc_bank.exceptions.BadRequestException;
import com.abc_bank.abc_bank.exceptions.NotFoundException;
import com.abc_bank.abc_bank.notification.dtos.NotificationDTO;
import com.abc_bank.abc_bank.notification.services.NotificationService;
import com.abc_bank.abc_bank.res.Response;
import com.abc_bank.abc_bank.role.entity.Role;
import com.abc_bank.abc_bank.role.repo.RoleRepo;
import com.abc_bank.abc_bank.security.TokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepo userRepo;
    @Mock private RoleRepo roleRepo;
    @Mock private PasswordResetCodeRepo passwordResetCodeRepo;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private TokenService tokenService;
    @Mock private NotificationService notificationService;
    @Mock private ModelMapper modelMapper;

    @InjectMocks private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        lenient().when(modelMapper.map(any(User.class), eq(UserDTO.class)))
                .thenAnswer(invocation -> {
                    User u = invocation.getArgument(0);
                    UserDTO dto = new UserDTO();
                    dto.setId(u.getId());
                    dto.setEmail(u.getEmail());
                    dto.setFirstName(u.getFirstName());
                    dto.setLastName(u.getLastName());
                    dto.setActive(u.isActive());
                    dto.setPassword(u.getPassword());
                    return dto;
                });
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private RegistrationRequest validRegistration() {
        RegistrationRequest r = new RegistrationRequest();
        r.setFirstName("Alice");
        r.setLastName("Smith");
        r.setEmail("alice@example.com");
        r.setPassword("rawPass");
        return r;
    }

    // ---------- registerUser ----------

    @Test
    void registerUser_whenEmailAlreadyExists_throwsBadRequest() {
        RegistrationRequest req = validRegistration();
        when(userRepo.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> userService.registerUser(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");

        verify(userRepo, never()).save(any());
    }

    @Test
    void registerUser_whenRolesNullOrEmpty_assignsDefaultRoleCustomer() {
        RegistrationRequest req = validRegistration();
        req.setRoles(null);

        when(userRepo.findByEmail(anyString())).thenReturn(Optional.empty());
        when(roleRepo.findByName("ROLE_CUSTOMER"))
                .thenReturn(Optional.of(Role.builder().id(1L).name("ROLE_CUSTOMER").build()));
        when(passwordEncoder.encode("rawPass")).thenReturn("encoded");
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.registerUser(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(captor.capture());
        assertThat(captor.getValue().getRoles())
                .extracting(Role::getName)
                .containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void registerUser_whenRoleDoesNotExistInDb_createsNewRole() {
        RegistrationRequest req = validRegistration();
        req.setRoles(List.of("ROLE_ADMIN"));

        when(userRepo.findByEmail(anyString())).thenReturn(Optional.empty());
        when(roleRepo.findByName("ROLE_ADMIN")).thenReturn(Optional.empty());
        when(roleRepo.save(any(Role.class)))
                .thenAnswer(inv -> {
                    Role r = inv.getArgument(0);
                    r.setId(99L);
                    return r;
                });
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.registerUser(req);

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepo).save(roleCaptor.capture());
        assertThat(roleCaptor.getValue().getName()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void registerUser_encodesPasswordAndPersistsActiveTrueWithCreatedAt() {
        RegistrationRequest req = validRegistration();

        when(userRepo.findByEmail(anyString())).thenReturn(Optional.empty());
        when(roleRepo.findByName(anyString()))
                .thenReturn(Optional.of(Role.builder().id(1L).name("ROLE_CUSTOMER").build()));
        when(passwordEncoder.encode("rawPass")).thenReturn("encoded-secret");
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.registerUser(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(captor.capture());
        User persisted = captor.getValue();
        assertThat(persisted.getPassword()).isEqualTo("encoded-secret");
        assertThat(persisted.isActive()).isTrue();
        assertThat(persisted.getCreatedAt()).isNotNull();
    }

    @Test
    void registerUser_returnsCreatedStatusAndNullsPasswordInDto() {
        RegistrationRequest req = validRegistration();

        when(userRepo.findByEmail(anyString())).thenReturn(Optional.empty());
        when(roleRepo.findByName(anyString()))
                .thenReturn(Optional.of(Role.builder().id(1L).name("ROLE_CUSTOMER").build()));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L);
            return u;
        });

        Response<UserDTO> response = userService.registerUser(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(response.getData().getPassword()).isNull();
        assertThat(response.getData().getId()).isEqualTo(42L);
    }

    // ---------- loginUser ----------

    private User activeUser() {
        return User.builder()
                .id(1L)
                .email("alice@example.com")
                .password("encoded")
                .active(true)
                .roles(List.of(Role.builder().name("ROLE_CUSTOMER").build()))
                .build();
    }

    @Test
    void loginUser_whenBadCredentials_throwsBadRequest() {
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@example.com");
        req.setPassword("wrong");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("nope"));

        assertThatThrownBy(() -> userService.loginUser(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("invalid email or password");

        verify(tokenService, never()).generateToken(anyString());
    }

    @Test
    void loginUser_whenUserMissingAfterAuth_throwsNotFound() {
        LoginRequest req = new LoginRequest();
        req.setEmail("ghost@example.com");
        req.setPassword("pw");

        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepo.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.loginUser(req))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void loginUser_whenUserInactive_throwsBadRequest() {
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@example.com");
        req.setPassword("pw");

        User inactive = activeUser();
        inactive.setActive(false);

        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepo.findByEmail(anyString())).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> userService.loginUser(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("deactivated");

        verify(tokenService, never()).generateToken(anyString());
    }

    @Test
    void loginUser_whenRolesNull_returnsEmptyRoleList() {
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@example.com");
        req.setPassword("pw");

        User user = activeUser();
        user.setRoles(null);

        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepo.findByEmail(anyString())).thenReturn(Optional.of(user));
        when(tokenService.generateToken("alice@example.com")).thenReturn("jwt-token");

        Response<LoginResponse> response = userService.loginUser(req);

        assertThat(response.getData().getRoles()).isEmpty();
        assertThat(response.getData().getToken()).isEqualTo("jwt-token");
    }

    @Test
    void loginUser_returnsTokenAndRoleNames() {
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@example.com");
        req.setPassword("pw");

        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepo.findByEmail(anyString())).thenReturn(Optional.of(activeUser()));
        when(tokenService.generateToken("alice@example.com")).thenReturn("jwt-token");

        Response<LoginResponse> response = userService.loginUser(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getData().getToken()).isEqualTo("jwt-token");
        assertThat(response.getData().getRoles()).containsExactly("ROLE_CUSTOMER");
    }

    // ---------- updatePassword ----------

    @Test
    void updatePassword_whenOldPasswordWrong_throwsBadRequest() {
        UpdatePasswordRequest req = new UpdatePasswordRequest();
        req.setOldPassword("wrong");
        req.setNewPassword("new");

        authenticateAs("alice@example.com");
        when(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser()));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> userService.updatePassword(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("old password is incorrect");

        verify(userRepo, never()).save(any());
    }

    @Test
    void updatePassword_encodesNewPasswordAndPersists() {
        UpdatePasswordRequest req = new UpdatePasswordRequest();
        req.setOldPassword("rawOld");
        req.setNewPassword("rawNew");

        authenticateAs("alice@example.com");
        User user = activeUser();
        when(userRepo.findByEmail(anyString())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("rawOld", "encoded")).thenReturn(true);
        when(passwordEncoder.encode("rawNew")).thenReturn("encoded-new");

        userService.updatePassword(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("encoded-new");
        assertThat(captor.getValue().getUpdatedAt()).isNotNull();
    }

    // ---------- requestPasswordReset ----------

    @Test
    void requestPasswordReset_whenUserNotFound_throwsNotFound() {
        when(userRepo.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.requestPasswordReset("missing@example.com"))
                .isInstanceOf(NotFoundException.class);

        verify(passwordResetCodeRepo, never()).save(any());
        verify(notificationService, never()).sendEmail(any(), any());
    }

    @Test
    void requestPasswordReset_deletesExistingCodesAndSavesNewWithFutureExpiry() {
        User user = activeUser();
        when(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        userService.requestPasswordReset("alice@example.com");

        verify(passwordResetCodeRepo).deleteByUserId(1L);

        ArgumentCaptor<PasswordResetCode> captor = ArgumentCaptor.forClass(PasswordResetCode.class);
        verify(passwordResetCodeRepo).save(captor.capture());
        PasswordResetCode saved = captor.getValue();
        assertThat(saved.getCode()).isNotBlank();
        assertThat(saved.isUsed()).isFalse();
        assertThat(saved.getExpiryDate()).isAfter(LocalDateTime.now().plusMinutes(29));
        assertThat(saved.getExpiryDate()).isBefore(LocalDateTime.now().plusMinutes(31));
    }

    @Test
    void requestPasswordReset_sendsEmailContainingTheGeneratedCode() {
        User user = activeUser();
        when(userRepo.findByEmail(anyString())).thenReturn(Optional.of(user));

        userService.requestPasswordReset("alice@example.com");

        ArgumentCaptor<PasswordResetCode> codeCaptor = ArgumentCaptor.forClass(PasswordResetCode.class);
        verify(passwordResetCodeRepo).save(codeCaptor.capture());

        ArgumentCaptor<NotificationDTO> notifCaptor = ArgumentCaptor.forClass(NotificationDTO.class);
        verify(notificationService).sendEmail(notifCaptor.capture(), eq(user));

        NotificationDTO sent = notifCaptor.getValue();
        assertThat(sent.getRecipient()).isEqualTo("alice@example.com");
        assertThat(sent.getBody()).contains(codeCaptor.getValue().getCode());
    }

    // ---------- resetPassword ----------

    @Test
    void resetPassword_whenCodeNull_throwsBadRequest() {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setNewPassword("new");

        assertThatThrownBy(() -> userService.resetPassword(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("code and newPassword are required");
    }

    @Test
    void resetPassword_whenNewPasswordNull_throwsBadRequest() {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setCode("abc");

        assertThatThrownBy(() -> userService.resetPassword(req))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void resetPassword_whenCodeNotFound_throwsBadRequest() {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setCode("missing");
        req.setNewPassword("new");

        when(passwordResetCodeRepo.findByCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.resetPassword(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("invalid reset code");
    }

    @Test
    void resetPassword_whenCodeAlreadyUsed_throwsBadRequest() {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setCode("abc");
        req.setNewPassword("new");

        PasswordResetCode entry = PasswordResetCode.builder()
                .code("abc")
                .user(activeUser())
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .used(true)
                .build();
        when(passwordResetCodeRepo.findByCode("abc")).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> userService.resetPassword(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void resetPassword_whenCodeExpired_throwsBadRequest() {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setCode("abc");
        req.setNewPassword("new");

        PasswordResetCode entry = PasswordResetCode.builder()
                .code("abc")
                .user(activeUser())
                .expiryDate(LocalDateTime.now().minusMinutes(1))
                .used(false)
                .build();
        when(passwordResetCodeRepo.findByCode("abc")).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> userService.resetPassword(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void resetPassword_whenEmailMismatch_throwsBadRequest() {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setCode("abc");
        req.setNewPassword("new");
        req.setEmail("other@example.com");

        PasswordResetCode entry = PasswordResetCode.builder()
                .code("abc")
                .user(activeUser())
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .used(false)
                .build();
        when(passwordResetCodeRepo.findByCode("abc")).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> userService.resetPassword(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void resetPassword_onSuccess_encodesPasswordAndMarksCodeUsed() {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setCode("abc");
        req.setNewPassword("rawNew");
        req.setEmail("alice@example.com");

        User user = activeUser();
        PasswordResetCode entry = PasswordResetCode.builder()
                .code("abc")
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .used(false)
                .build();
        when(passwordResetCodeRepo.findByCode("abc")).thenReturn(Optional.of(entry));
        when(passwordEncoder.encode("rawNew")).thenReturn("encoded-new");

        userService.resetPassword(req);

        assertThat(user.getPassword()).isEqualTo("encoded-new");
        verify(userRepo).save(user);

        ArgumentCaptor<PasswordResetCode> captor = ArgumentCaptor.forClass(PasswordResetCode.class);
        verify(passwordResetCodeRepo).save(captor.capture());
        assertThat(captor.getValue().isUsed()).isTrue();
    }

    // ---------- getCurrentUserEntity / getCurrentUser ----------

    @Test
    void getCurrentUserEntity_whenNoAuth_throwsBadRequest() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> userService.getCurrentUserEntity())
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no authenticated user");
    }

    @Test
    void getCurrentUserEntity_whenAuthenticatedButUserMissingFromDb_throwsNotFound() {
        authenticateAs("ghost@example.com");
        when(userRepo.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCurrentUserEntity())
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getCurrentUser_returnsDtoWithoutPassword() {
        authenticateAs("alice@example.com");
        when(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser()));

        Response<UserDTO> response = userService.getCurrentUser();

        assertThat(response.getData().getPassword()).isNull();
        assertThat(response.getData().getEmail()).isEqualTo("alice@example.com");
    }

    // ---------- getUserById ----------

    @Test
    void getUserById_whenNotFound_throwsNotFound() {
        when(userRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getUserById_returnsDtoWithoutPassword() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(activeUser()));

        Response<UserDTO> response = userService.getUserById(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getData().getPassword()).isNull();
    }

    // ---------- helpers ----------

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", new ArrayList<>())
        );
    }
}
