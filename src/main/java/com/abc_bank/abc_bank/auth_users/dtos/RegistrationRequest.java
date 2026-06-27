package com.abc_bank.abc_bank.auth_users.dtos;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegistrationRequest {

    @NotBlank(message="firstname is required")
    private String firstName;

    private String lastName;
    @NotBlank(message="email is required")
    @Email
    private String email;

    @NotBlank(message="password is required")
    @Size(min=8, message="password must be at least 8 characters")
    private String password;
}
