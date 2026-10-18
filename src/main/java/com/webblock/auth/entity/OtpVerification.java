package com.webblock.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row in the "otp_verification" table.
 * This is the "waiting room" for Register and Forgot Password.
 * The row is deleted when the flow is finished.
 */
@Entity
@Table(name = "otp_verification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpVerification {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    /** Only used for REGISTER: kept until the account is created. */
    @Column(name = "full_name", length = 100)
    private String fullName;

    /** The OTP is hashed. We never save it in plain text. */
    @Column(name = "otp_hash", nullable = false)
    private String otpHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OtpPurpose purpose;

    /** How many wrong tries so far (max 5). */
    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private boolean verified;

    /** Given after the OTP is verified. Proves the user may set a password. */
    @Column(name = "verification_token", unique = true)
    private String verificationToken;

    /** The OTP is good until this time (5 minutes). */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** The verificationToken is good until this time (15 minutes). */
    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = LocalDateTime.now();
    }
}
