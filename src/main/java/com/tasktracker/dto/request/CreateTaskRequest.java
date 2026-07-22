package com.tasktracker.dto.request;

import com.tasktracker.entity.Priority;
import com.tasktracker.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateTaskRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    private String description;

    @NotNull(message = "Task date is required")
    private LocalDate taskDate;

    private Priority priority = Priority.MEDIUM;

    private TaskStatus status = TaskStatus.TODO;

    @Size(max = 50, message = "Category must not exceed 50 characters")
    private String category;
}
