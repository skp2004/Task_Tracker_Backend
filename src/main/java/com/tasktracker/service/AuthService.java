package com.tasktracker.service;

import com.tasktracker.dto.request.*;
import com.tasktracker.dto.response.JwtAuthResponse;
import com.tasktracker.entity.User;
import com.tasktracker.exception.BadRequestException;
import com.tasktracker.exception.ConflictException;
import com.tasktracker.exception.ResourceNotFoundException;
import com.tasktracker.repository.UserRepository;
import com.tasktracker.security.JwtTokenProvider;
import com.tasktracker.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public JwtAuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email address is already registered: " + request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .phone(request.getPhone())
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {} (id={})", user.getEmail(), user.getId());

        String accessToken = jwtTokenProvider.generateAccessTokenFromId(user.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return buildResponse(user, accessToken, refreshToken);
    }

    @Transactional(readOnly = true)
    public JwtAuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String accessToken = jwtTokenProvider.generateAccessToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        log.info("User logged in: {}", user.getEmail());
        return buildResponse(user, accessToken, refreshToken);
    }

    @Transactional(readOnly = true)
    public JwtAuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new ResourceNotFoundException("Invalid or expired refresh token");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String newAccessToken = jwtTokenProvider.generateAccessTokenFromId(user.getId());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        log.info("Token refreshed for user: {}", user.getEmail());
        return buildResponse(user, newAccessToken, newRefreshToken);
    }

    private final OtpService otpService;

    @Transactional
    public Map<String, String> forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("No user account found with email: " + email));

        String otp = otpService.generateOtp(user.getEmail());
        return Map.of(
                "message", "OTP has been sent to your email address.",
                "email", user.getEmail(),
                "otp", otp // Included for seamless testing & preview
        );
    }

    public Map<String, String> verifyOtp(VerifyOtpRequest request) {
        boolean valid = otpService.verifyOtp(request.getEmail(), request.getOtp());
        if (valid) {
            return Map.of("message", "OTP verified successfully.", "status", "VERIFIED");
        } else {
            throw new BadRequestException("Invalid or expired OTP.");
        }
    }

    @Transactional
    public Map<String, String> resetPassword(ResetPasswordRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        if (!otpService.isOtpVerified(email, request.getOtp())) {
            throw new BadRequestException("OTP is not verified or expired. Please verify OTP first.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        otpService.clearOtp(email);
        log.info("Password reset successfully for user: {}", email);
        return Map.of("message", "Password reset successfully. You can now login with your new password.");
    }

    private JwtAuthResponse buildResponse(User user, String accessToken, String refreshToken) {
        return JwtAuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole().name())
                .build();
    }
}
