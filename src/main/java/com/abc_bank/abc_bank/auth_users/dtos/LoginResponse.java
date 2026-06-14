package com.abc_bank.abc_bank.auth_users.dtos;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {
    private String token;
    private List<String> roles;
    private Boolean mfaRequired;
    private String challengeId;
}
