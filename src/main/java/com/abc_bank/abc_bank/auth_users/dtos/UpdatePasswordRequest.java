package com.abc_bank.abc_bank.auth_users.dtos;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdatePasswordRequest {
    @NotBlank(message="old password is required")
    private String oldPassword;

    @NotBlank(message="new password is required")
    @Size(min=8, message="new password must be at least 8 characters")
    private String newPassword;
}
