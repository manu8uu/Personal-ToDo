package com.mentor.app.dao;

import com.mentor.app.domain.OccurrenceStatus;
import com.mentor.app.domain.TaskOccurrence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TaskOccurrenceDao extends JpaRepository<TaskOccurrence, Long> {
    List<TaskOccurrence> findByOccurrenceDateBetweenOrderByOccurrenceDateAscIdAsc(LocalDate from, LocalDate to);

    List<TaskOccurrence> findByTaskIdAndOccurrenceDateBetween(Long taskId, LocalDate from, LocalDate to);

    List<TaskOccurrence> findByOccurrenceDateLessThanEqualOrderByOccurrenceDateAsc(LocalDate date);

    @Modifying(clearAutomatically = true)
    @Query("update TaskOccurrence o set o.status = :newStatus "
            + "where o.status = :oldStatus and o.occurrenceDate < :before")
    int updateStatusBefore(
            @Param("oldStatus") OccurrenceStatus oldStatus,
            @Param("newStatus") OccurrenceStatus newStatus,
            @Param("before") LocalDate before);


}