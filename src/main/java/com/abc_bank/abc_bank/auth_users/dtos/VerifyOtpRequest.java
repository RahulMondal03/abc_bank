package com.abc_bank.abc_bank.auth_users.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyOtpRequest {
    @NotBlank(message="challengeId is required")
    private String challengeId;

    @NotBlank(message="code is required")
    private String code;
}
