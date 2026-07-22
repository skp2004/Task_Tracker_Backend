package com.tasktracker.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CalendarMonthResponse {
    /** List of ISO dates (yyyy-MM-dd) that have at least one task */
    private List<String> datesWithTasks;
    private int year;
    private int month;
}
