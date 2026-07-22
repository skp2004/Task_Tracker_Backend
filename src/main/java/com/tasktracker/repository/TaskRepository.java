package com.tasktracker.repository;

import com.tasktracker.entity.Task;
import com.tasktracker.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    /** All tasks for a user on a specific date */
    List<Task> findByUserIdAndTaskDateOrderByCreatedAtAsc(Long userId, LocalDate date);

    /** All tasks for a user in a date range (for month/year/custom reports) */
    List<Task> findByUserIdAndTaskDateBetweenOrderByTaskDateAscCreatedAtAsc(
            Long userId, LocalDate from, LocalDate to);

    /** Count tasks per status for a user in a date range */
    @Query("""
        SELECT t.status AS status, COUNT(t) AS count
        FROM Task t
        WHERE t.user.id = :userId
          AND t.taskDate BETWEEN :from AND :to
        GROUP BY t.status
    """)
    List<Object[]> countByStatusInRange(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Distinct dates that have tasks (for calendar dots) */
    @Query("""
        SELECT DISTINCT t.taskDate
        FROM Task t
        WHERE t.user.id = :userId
          AND t.taskDate BETWEEN :from AND :to
        ORDER BY t.taskDate
    """)
    List<LocalDate> findDatesWithTasksInRange(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    boolean existsByIdAndUserId(Long id, Long userId);
}
