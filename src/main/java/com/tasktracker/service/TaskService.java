package com.tasktracker.service;

import com.tasktracker.dto.request.CreateTaskRequest;
import com.tasktracker.dto.request.UpdateTaskRequest;
import com.tasktracker.dto.response.CalendarMonthResponse;
import com.tasktracker.dto.response.TaskResponse;
import com.tasktracker.entity.Task;
import com.tasktracker.entity.User;
import com.tasktracker.exception.ResourceNotFoundException;
import com.tasktracker.repository.TaskRepository;
import com.tasktracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    // ─── Read ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksByDate(Long userId, LocalDate date) {
        return taskRepository
                .findByUserIdAndTaskDateOrderByCreatedAtAsc(userId, date)
                .stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksByDateRange(Long userId, LocalDate from, LocalDate to) {
        return taskRepository
                .findByUserIdAndTaskDateBetweenOrderByTaskDateAscCreatedAtAsc(userId, from, to)
                .stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CalendarMonthResponse getCalendarMonth(Long userId, int year, int month) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());

        List<String> dates = taskRepository
                .findDatesWithTasksInRange(userId, from, to)
                .stream()
                .map(d -> d.format(ISO))
                .toList();

        return CalendarMonthResponse.builder()
                .year(year)
                .month(month)
                .datesWithTasks(dates)
                .build();
    }

    // ─── Write ───────────────────────────────────────────────

    @Transactional
    public TaskResponse createTask(Long userId, CreateTaskRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Task task = Task.builder()
                .user(user)
                .title(req.getTitle().trim())
                .description(req.getDescription())
                .taskDate(req.getTaskDate())
                .priority(req.getPriority())
                .status(req.getStatus())
                .category(req.getCategory())
                .build();

        task = taskRepository.save(task);
        log.debug("Task created: id={} user={} date={}", task.getId(), userId, task.getTaskDate());
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse updateTask(Long userId, Long taskId, UpdateTaskRequest req) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + taskId));

        if (!task.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Task not found with id: " + taskId);
        }

        if (req.getTitle() != null)       task.setTitle(req.getTitle().trim());
        if (req.getDescription() != null) task.setDescription(req.getDescription());
        if (req.getTaskDate() != null)    task.setTaskDate(req.getTaskDate());
        if (req.getPriority() != null)    task.setPriority(req.getPriority());
        if (req.getStatus() != null)      task.setStatus(req.getStatus());
        if (req.getCategory() != null)    task.setCategory(req.getCategory());

        task = taskRepository.save(task);
        log.debug("Task updated: id={} user={}", taskId, userId);
        return TaskResponse.from(task);
    }

    @Transactional
    public void deleteTask(Long userId, Long taskId) {
        if (!taskRepository.existsByIdAndUserId(taskId, userId)) {
            throw new ResourceNotFoundException("Task not found with id: " + taskId);
        }
        taskRepository.deleteById(taskId);
        log.debug("Task deleted: id={} user={}", taskId, userId);
    }
}
