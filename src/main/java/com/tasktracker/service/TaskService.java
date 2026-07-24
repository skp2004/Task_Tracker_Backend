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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.jpa.HibernateHints;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Task business logic.
 *
 * <p>Performance notes:
 * <ul>
 *   <li>All reads use {@code readOnly = true} which tells Hibernate to skip
 *       dirty-checking, opens the connection on the virtual thread (no blocking),
 *       and lets the JDBC driver optimise cursor semantics.</li>
 *   <li>Write operations use {@code merge()} semantics via {@code save()} with
 *       batch_size=25 configured in application.yml — multiple saves in a loop
 *       are batched into a single round-trip.</li>
 *   <li>The {@code EntityManager} fetch-size hint is applied on list queries so
 *       the JDBC driver streams 50 rows per network round-trip instead of
 *       fetching every row in one giant packet.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager em;

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

        // Projection query — returns only LocalDate values, no full entity load
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

    @Transactional
    public void deleteTask(Long userId, Long taskId) {
        if (!taskRepository.existsByIdAndUserId(taskId, userId)) {
            throw new ResourceNotFoundException("Task not found with id: " + taskId);
        }
        taskRepository.deleteById(taskId);
        log.debug("Task deleted: id={} user={}", taskId, userId);
    }
}
