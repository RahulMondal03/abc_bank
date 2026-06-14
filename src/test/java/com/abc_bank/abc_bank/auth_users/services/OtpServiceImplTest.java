package com.abc_bank.abc_bank.auth_users.services;

import com.abc_bank.abc_bank.auth_users.entity.OtpCode;
import com.abc_bank.abc_bank.auth_users.entity.User;
import com.abc_bank.abc_bank.auth_users.repo.OtpCodeRepo;
import com.abc_bank.abc_bank.exceptions.BadRequestException;
import com.abc_bank.abc_bank.notification.dtos.NotificationDTO;
import com.abc_bank.abc_bank.notification.services.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceImplTest {

    @Mock
    private OtpCodeRepo otpCodeRepo;

    @Mock
    private NotificationService notificationService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @InjectMocks
    private OtpServiceImpl otpService;

    private User user;

    @BeforeEach
    void setUp() {
        // @InjectMocks won't supply the real encoder, so set it explicitly.
        ReflectionTestUtils.setField(otpService, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(otpService, "otpLength", 6);
        ReflectionTestUtils.setField(otpService, "otpTtlMinutes", 5L);
        ReflectionTestUtils.setField(otpService, "otpMaxAttempts", 5);

        user = User.builder()
                .id(1L)
                .email("jane@example.com")
                .active(true)
                .build();
    }

    @Test
    void issueOtp_clearsOldCodes_persists_andEmailsSixDigitCode() {
        String challengeId = otpService.issueOtp(user);

        assertThat(challengeId).isNotBlank();
        verify(otpCodeRepo).deleteByUserId(1L);

        ArgumentCaptor<OtpCode> savedCaptor = ArgumentCaptor.forClass(OtpCode.class);
        verify(otpCodeRepo).save(savedCaptor.capture());
        OtpCode saved = savedCaptor.getValue();
        assertThat(saved.getChallengeId()).isEqualTo(challengeId);
        assertThat(saved.isUsed()).isFalse();
        assertThat(saved.getAttemptCount()).isZero();
        assertThat(saved.getExpiryDate()).isAfter(LocalDateTime.now());
        // Code is stored hashed, never in plaintext.
        assertThat(saved.getCodeHash()).isNotBlank();

        ArgumentCaptor<NotificationDTO> emailCaptor = ArgumentCaptor.forClass(NotificationDTO.class);
        verify(notificationService).sendEmail(emailCaptor.capture(), any(User.class));
        NotificationDTO email = emailCaptor.getValue();
        assertThat(email.getRecipient()).isEqualTo("jane@example.com");
        String code = extractCode(email.getBody());
        assertThat(code).hasSize(6).containsOnlyDigits();
        // The emailed plaintext code matches the stored hash.
        assertThat(passwordEncoder.matches(code, saved.getCodeHash())).isTrue();
    }

    @Test
    void verifyOtp_withCorrectCode_marksUsed_andReturnsUser() {
        String challengeId = otpService.issueOtp(user);
        OtpCode saved = capturedSavedCode();
        String code = extractCode(capturedEmail().getBody());
        when(otpCodeRepo.findByChallengeId(challengeId)).thenReturn(Optional.of(saved));

        User result = otpService.verifyOtp(challengeId, code);

        assertThat(result).isEqualTo(user);
        assertThat(saved.isUsed()).isTrue();
    }

    @Test
    void verifyOtp_withWrongCode_incrementsAttempts_andThrows() {
        String challengeId = otpService.issueOtp(user);
        OtpCode saved = capturedSavedCode();
        when(otpCodeRepo.findByChallengeId(challengeId)).thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> otpService.verifyOtp(challengeId, "000000"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("invalid verification code");

        assertThat(saved.getAttemptCount()).isEqualTo(1);
        assertThat(saved.isUsed()).isFalse();
    }

    @Test
    void verifyOtp_whenMaxAttemptsExceeded_throws() {
        String challengeId = otpService.issueOtp(user);
        OtpCode saved = capturedSavedCode();
        saved.setAttemptCount(5);
        when(otpCodeRepo.findByChallengeId(challengeId)).thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> otpService.verifyOtp(challengeId, "123456"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("too many invalid attempts");
    }

    @Test
    void verifyOtp_whenExpired_throws() {
        String challengeId = otpService.issueOtp(user);
        OtpCode saved = capturedSavedCode();
        saved.setExpiryDate(LocalDateTime.now().minusMinutes(1));
        when(otpCodeRepo.findByChallengeId(challengeId)).thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> otpService.verifyOtp(challengeId, "123456"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void verifyOtp_whenAlreadyUsed_throws() {
        String challengeId = otpService.issueOtp(user);
        OtpCode saved = capturedSavedCode();
        saved.setUsed(true);
        when(otpCodeRepo.findByChallengeId(challengeId)).thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> otpService.verifyOtp(challengeId, "123456"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void verifyOtp_withUnknownChallenge_throws() {
        when(otpCodeRepo.findByChallengeId("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> otpService.verifyOtp("nope", "123456"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("invalid verification code");
        verify(otpCodeRepo, never()).save(any());
    }

    @Test
    void resendOtp_issuesFreshCodeForSameUser() {
        OtpCode existing = OtpCode.builder().challengeId("old").user(user).build();
        when(otpCodeRepo.findByChallengeId("old")).thenReturn(Optional.of(existing));

        String newChallengeId = otpService.resendOtp("old");

        assertThat(newChallengeId).isNotBlank().isNotEqualTo("old");
        verify(otpCodeRepo).deleteByUserId(1L);
        verify(notificationService, times(1)).sendEmail(any(NotificationDTO.class), any(User.class));
    }

    private OtpCode capturedSavedCode() {
        ArgumentCaptor<OtpCode> captor = ArgumentCaptor.forClass(OtpCode.class);
        verify(otpCodeRepo).save(captor.capture());
        return captor.getValue();
    }

    private NotificationDTO capturedEmail() {
        ArgumentCaptor<NotificationDTO> captor = ArgumentCaptor.forClass(NotificationDTO.class);
        verify(notificationService).sendEmail(captor.capture(), any(User.class));
        return captor.getValue();
    }

    private String extractCode(String body) {
        // Body format: "Your login verification code is: 123456. It expires ..."
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(\\d{6})\\b").matcher(body);
        assertThat(m.find()).as("body contains a 6-digit code").isTrue();
        return m.group(1);
    }
}
