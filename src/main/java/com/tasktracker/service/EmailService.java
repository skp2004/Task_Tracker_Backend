package com.tasktracker.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:shashwatpatel04@gmail.com}")
    private String fromEmail;

    @Async
    public void sendOtpEmail(String toEmail, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "TaskTracker Security");
            helper.setTo(toEmail);
            helper.setSubject("Your TaskTracker OTP Password Reset Code");

            String htmlBody = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f4f4f7; margin: 0; padding: 20px; }
                        .card { max-width: 500px; margin: 0 auto; background: #ffffff; border-radius: 16px; padding: 32px; box-shadow: 0 4px 12px rgba(0,0,0,0.05); }
                        .logo { font-size: 22px; font-weight: 800; color: #111111; margin-bottom: 24px; text-align: center; }
                        .title { font-size: 18px; font-weight: 700; color: #222222; margin-bottom: 12px; }
                        .text { font-size: 14px; color: #555555; line-height: 1.6; margin-bottom: 24px; }
                        .otp-box { background: #f8f9fa; border: 2px dashed #000000; border-radius: 12px; padding: 18px; text-align: center; margin-bottom: 24px; }
                        .otp-code { font-size: 32px; font-weight: 900; letter-spacing: 6px; color: #000000; }
                        .footer { font-size: 12px; color: #888888; text-align: center; border-top: 1px solid #eeeeee; padding-top: 16px; margin-top: 24px; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <div class="logo">TaskTracker</div>
                        <div class="title">Password Reset Verification</div>
                        <div class="text">
                            Hello,<br>
                            You recently requested to reset your password for your TaskTracker account. Use the 6-digit OTP code below to verify your identity:
                        </div>
                        <div class="otp-box">
                            <div class="otp-code">%s</div>
                        </div>
                        <div class="text" style="font-size: 13px; color: #777777;">
                            This OTP is valid for <strong>10 minutes</strong>. If you did not request a password reset, please ignore this email.
                        </div>
                        <div class="footer">
                            &copy; TaskTracker Application • Secure Auth System
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(otp);

            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("✉️ OTP Email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            log.error("❌ Failed to send OTP email to {}: {}", toEmail, e.getMessage(), e);
        }
    }
}
