package com.tasktracker.service;

import com.tasktracker.dto.request.CreateTaskRequest;
import com.tasktracker.dto.request.UpdateTaskRequest;
import com.tasktracker.dto.response.CalendarMonthResponse;
import com.tasktracker.dto.response.TaskResponse;
import com.tasktracker.entity.Task;
import com.tasktracker.entity.User;
import com.tasktracker.exception.BadRequestException;
import com.tasktracker.exception.ResourceNotFoundException;
import com.tasktracker.repository.TaskRepository;
import com.tasktracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Task business logic with soft-delete (trash) support.
 *
 * <p>Delete strategy:
 * <ul>
 *   <li>{@code deleteTask} — soft-delete: sets {@code deletedAt = now()}, task moves to trash.</li>
 *   <li>{@code restoreTask} — clears {@code deletedAt}, task comes back to active.</li>
 *   <li>{@code permanentlyDeleteTask} — hard DELETE from DB, only works on trashed tasks.</li>
 *   <li>{@code getTrashedTasks} — returns all tasks where {@code deletedAt IS NOT NULL}.</li>
 * </ul>
 * The {@code @SQLRestriction("deleted_at IS NULL")} on the entity filters out trashed
 * tasks from every normal JPA query automatically.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    // ─── Read ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksByDate(Long userId, LocalDate date) {
        List<Task> tasks = taskRepository
                .findByUserIdAndTaskDateOrderByCreatedAtAsc(userId, date);
        log.debug("getTasksByDate uid={} date={} -> {} tasks", userId, date, tasks.size());
        return tasks.stream().map(TaskResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksByDateRange(Long userId, LocalDate from, LocalDate to) {
        List<Task> tasks = taskRepository
                .findByUserIdAndTaskDateBetweenOrderByTaskDateAscCreatedAtAsc(userId, from, to);
        log.debug("getTasksByDateRange uid={} from={} to={} -> {} tasks", userId, from, to, tasks.size());
        return tasks.stream().map(TaskResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CalendarMonthResponse getCalendarMonth(Long userId, int year, int month) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to   = from.withDayOfMonth(from.lengthOfMonth());

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

    // ─── Write ───────────────────────────────────────────────────────────────

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

    // ─── Soft-delete (Trash) ─────────────────────────────────────────────────

    /** Soft-delete: moves task to trash by setting deletedAt timestamp. */
    @Transactional
    public void deleteTask(Long userId, Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + taskId));

        if (!task.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Task not found with id: " + taskId);
        }

        task.setDeletedAt(OffsetDateTime.now());
        taskRepository.save(task);
        log.debug("Task soft-deleted (trashed): id={} user={}", taskId, userId);
    }

    /** Returns all tasks that have been soft-deleted (in trash) for this user. */
    @Transactional(readOnly = true)
    public List<TaskResponse> getTrashedTasks(Long userId) {
        return taskRepository.findTrashedByUserId(userId)
                .stream()
                .map(TaskResponse::from)
                .toList();
    }

    /** Restore: clears deletedAt, task becomes active again. */
    @Transactional
    public TaskResponse restoreTask(Long userId, Long taskId) {
        Task task = taskRepository.findAnyByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found in trash with id: " + taskId));

        if (task.getDeletedAt() == null) {
            throw new BadRequestException("Task is not in trash.");
        }

        task.setDeletedAt(null);
        task = taskRepository.save(task);
        log.debug("Task restored from trash: id={} user={}", taskId, userId);
        return TaskResponse.from(task);
    }

    /** Permanently deletes a trashed task. Only works on tasks that are in trash. */
    @Transactional
    public void permanentlyDeleteTask(Long userId, Long taskId) {
        int affected = taskRepository.permanentlyDelete(taskId, userId);
        if (affected == 0) {
            throw new ResourceNotFoundException("Task not found in trash with id: " + taskId);
        }
        log.debug("Task permanently deleted: id={} user={}", taskId, userId);
    }
}
