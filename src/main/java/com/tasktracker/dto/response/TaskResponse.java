package com.tasktracker.dto.response;

import com.tasktracker.entity.Task;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@Builder
public class TaskResponse {
    private Long id;
    private String title;
    private String description;
    private LocalDate taskDate;
    private String category;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;

    public static TaskResponse from(Task task) {
        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .taskDate(task.getTaskDate())
                .category(task.getCategory())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .deletedAt(task.getDeletedAt())
                .build();
    }
}
