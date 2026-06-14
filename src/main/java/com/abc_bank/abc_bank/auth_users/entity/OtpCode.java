package com.abc_bank.abc_bank.auth_users.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@Builder
@Table(name="otp_code")
@AllArgsConstructor
@NoArgsConstructor
public class OtpCode {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    @Column(unique=true, nullable=false)
    private String challengeId;

    @Column(nullable=false)
    private String codeHash;

    @ManyToOne(targetEntity = User.class, fetch=FetchType.EAGER)
    @JoinColumn(nullable=false, name="user_id")
    private User user;

    private LocalDateTime expiryDate;

    private boolean used;

    private int attemptCount;
}
