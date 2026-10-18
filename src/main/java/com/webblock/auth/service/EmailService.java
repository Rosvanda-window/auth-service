package com.webblock.auth.service;

import com.webblock.auth.entity.OtpPurpose;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends emails. In dev they go to Mailpit (http://localhost:8025).
 * In production you only change spring.mail.* in application.yaml.
 */
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.otp.expire-minutes}")
    private int expireMinutes;

    public void sendOtp(String to, String otp, OtpPurpose purpose) {
        String subject = purpose == OtpPurpose.REGISTER
                ? "WebBlock - your verification code"
                : "WebBlock - reset your password";

        String text = """
                Hello,

                Your WebBlock verification code is: %s

                It is valid for %d minutes.
                If you did not ask for this, you can ignore this email.
                """.formatted(otp, expireMinutes);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        mailSender.send(message);
    }
}
