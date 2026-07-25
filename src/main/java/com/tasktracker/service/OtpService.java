package com.tasktracker.service;

import com.tasktracker.exception.BadRequestException;
import com.tasktracker.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class OtpService {

    private static final long OTP_VALID_DURATION_MS = 10 * 60 * 1000; // 10 minutes
    private final Map<String, OtpEntry> otpData = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    private record OtpEntry(String otp, long expiryTime, boolean verified) {}

    public String generateOtp(String email) {
        String key = email.toLowerCase().trim();
        String otp = String.format("%06d", random.nextInt(1_000_000));
        long expiryTime = System.currentTimeMillis() + OTP_VALID_DURATION_MS;

        otpData.put(key, new OtpEntry(otp, expiryTime, false));
        log.info("🔑 [OTP GENERATED] Email: {} | OTP: {} (expires in 10 mins)", key, otp);
        return otp;
    }

    public boolean verifyOtp(String email, String otp) {
        String key = email.toLowerCase().trim();
        OtpEntry entry = otpData.get(key);

        if (entry == null) {
            throw new ResourceNotFoundException("No OTP found for this email address. Please request a new one.");
        }

        if (System.currentTimeMillis() > entry.expiryTime()) {
            otpData.remove(key);
            throw new BadRequestException("OTP has expired. Please request a new one.");
        }

        if (!entry.otp().equals(otp.trim())) {
            throw new BadRequestException("Invalid OTP code. Please check and try again.");
        }

        // Mark verified
        otpData.put(key, new OtpEntry(entry.otp(), entry.expiryTime(), true));
        log.info("✅ [OTP VERIFIED] Email: {}", key);
        return true;
    }

    public boolean isOtpVerified(String email, String otp) {
        String key = email.toLowerCase().trim();
        OtpEntry entry = otpData.get(key);

        if (entry == null || System.currentTimeMillis() > entry.expiryTime()) {
            return false;
        }

        return entry.otp().equals(otp.trim()) && entry.verified();
    }

    public void clearOtp(String email) {
        otpData.remove(email.toLowerCase().trim());
    }
}
