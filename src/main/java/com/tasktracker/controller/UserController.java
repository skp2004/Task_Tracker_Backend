package com.tasktracker.controller;

import com.tasktracker.dto.request.ChangePasswordRequest;
import com.tasktracker.dto.request.UpdateProfileRequest;
import com.tasktracker.dto.response.UserProfileResponse;
import com.tasktracker.entity.User;
import com.tasktracker.exception.BadRequestException;
import com.tasktracker.exception.ResourceNotFoundException;
import com.tasktracker.repository.UserRepository;
import com.tasktracker.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "User profile endpoints")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return ResponseEntity.ok(UserProfileResponse.from(user));
    }

    @PutMapping("/me")
    @Operation(summary = "Update current user profile (fullName, phone)")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setFullName(request.getFullName().trim());
        user.setPhone(request.getPhone());

        user = userRepository.save(user);
        return ResponseEntity.ok(UserProfileResponse.from(user));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change current user password")
    public ResponseEntity<Map<String, String>> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Password updated successfully."));
    }
}

