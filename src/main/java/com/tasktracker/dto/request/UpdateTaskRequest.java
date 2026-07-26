package com.tasktracker.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateTaskRequest {
    @Size(max = 255)
    private String title;

    private String description;

    private LocalDate taskDate;

    @Size(max = 50)
    private String category;
}
