package com.mentor.app.dao;

import com.mentor.app.domain.TaskOccurrence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TaskOccurrenceDao extends JpaRepository<TaskOccurrence, Long> {
    List<TaskOccurrence> findByOccurrenceDateBetweenOrderByOccurrenceDateAscIdAsc(LocalDate from, LocalDate to);
}