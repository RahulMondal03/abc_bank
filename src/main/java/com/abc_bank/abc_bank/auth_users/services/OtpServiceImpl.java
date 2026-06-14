package com.abc_bank.abc_bank.auth_users.services;

import com.abc_bank.abc_bank.auth_users.entity.OtpCode;
import com.abc_bank.abc_bank.auth_users.entity.User;
import com.abc_bank.abc_bank.auth_users.repo.OtpCodeRepo;
import com.abc_bank.abc_bank.enums.NotificationType;
import com.abc_bank.abc_bank.exceptions.BadRequestException;
import com.abc_bank.abc_bank.notification.dtos.NotificationDTO;
import com.abc_bank.abc_bank.notification.services.NotificationService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private final OtpCodeRepo otpCodeRepo;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${otp.length:6}")
    private int otpLength;

    @Value("${otp.ttl.minutes:5}")
    private long otpTtlMinutes;

    @Value("${otp.max-attempts:5}")
    private int otpMaxAttempts;

    @Override
    @Transactional
    public String issueOtp(User user) {
        otpCodeRepo.deleteByUserId(user.getId());

        String code = generateNumericCode(otpLength);
        String challengeId = UUID.randomUUID().toString();

        OtpCode entry = OtpCode.builder()
                .challengeId(challengeId)
                .codeHash(passwordEncoder.encode(code))
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(otpTtlMinutes))
                .used(false)
                .attemptCount(0)
                .build();
        otpCodeRepo.save(entry);

        NotificationDTO notification = NotificationDTO.builder()
                .recipient(user.getEmail())
                .subject("Your login verification code")
                .body("Your login verification code is: " + code
                        + ". It expires in " + otpTtlMinutes + " minutes.")
                .type(NotificationType.EMAIL)
                .build();
        notificationService.sendEmail(notification, user);

        return challengeId;
    }

    @Override
    @Transactional
    public User verifyOtp(String challengeId, String code) {
        OtpCode entry = otpCodeRepo.findByChallengeId(challengeId)
                .orElseThrow(() -> new BadRequestException("invalid verification code"));

        if (entry.isUsed()) {
            throw new BadRequestException("verification code has already been used");
        }
        if (entry.getExpiryDate() == null || entry.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("verification code has expired");
        }
        if (entry.getAttemptCount() >= otpMaxAttempts) {
            throw new BadRequestException("too many invalid attempts, please log in again");
        }

        if (!passwordEncoder.matches(code, entry.getCodeHash())) {
            entry.setAttemptCount(entry.getAttemptCount() + 1);
            otpCodeRepo.save(entry);
            throw new BadRequestException("invalid verification code");
        }

        entry.setUsed(true);
        otpCodeRepo.save(entry);

        return entry.getUser();
    }

    @Override
    @Transactional
    public String resendOtp(String challengeId) {
        OtpCode entry = otpCodeRepo.findByChallengeId(challengeId)
                .orElseThrow(() -> new BadRequestException("invalid verification code"));
        return issueOtp(entry.getUser());
    }

    private String generateNumericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(secureRandom.nextInt(10));
        }
        return sb.toString();
    }
}
