package com.abc_bank.abc_bank.auth_users.repo;

import com.abc_bank.abc_bank.auth_users.entity.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpCodeRepo extends JpaRepository<OtpCode, Long> {
    Optional<OtpCode> findByChallengeId(String challengeId);
    void deleteByUserId(Long userId);
}
