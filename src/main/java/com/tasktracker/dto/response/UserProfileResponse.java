package com.tasktracker.dto.response;

import com.tasktracker.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class UserProfileResponse {
    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String avatarUrl;
    private String role;
    private String collegeName;
    private String department;
    private String designation;
    private OffsetDateTime createdAt;

    public static UserProfileResponse from(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole().name())
                .collegeName(user.getCollegeName())
                .department(user.getDepartment())
                .designation(user.getDesignation())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
