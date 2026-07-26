package com.tasktracker.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JwtAuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long userId;
    private String email;
    private String fullName;
    private String phone;
    private String avatarUrl;
    private String role;
    private String collegeName;
    private String department;
    private String designation;
}
