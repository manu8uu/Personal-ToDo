package com.mentor.app.service;

import com.mentor.app.dao.TaskDao;
import com.mentor.app.dao.TaskOccurrenceDao;
import com.mentor.app.domain.OccurrenceStatus;
import com.mentor.app.domain.Task;
import com.mentor.app.domain.TaskOccurrence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecurrenceService {

    // cuántos días por delante mantenemos generados
    static final int HORIZON_DAYS = 28;

    private final TaskDao tasks;
    private final TaskOccurrenceDao occurrences;

    // crea las ocurrencias que falten; se puede llamar las veces que haga falta
    public int generateFor(Task task, LocalDate today) {
        LocalDate from = task.getStartDate().isAfter(today) ? task.getStartDate() : today;
        LocalDate to = today.plusDays(HORIZON_DAYS);
        if (task.getEndDate() != null && task.getEndDate().isBefore(to)) {
            to = task.getEndDate();
        }
        if (from.isAfter(to)) {
            return 0;
        }

        Recurrence rule = Recurrence.parse(task.getRecurrenceRule(), task.getStartDate());
        Set<LocalDate> existing = occurrences
                .findByTaskIdAndOccurrenceDateBetween(task.getId(), from, to).stream()
                .map(TaskOccurrence::getOccurrenceDate)
                .collect(Collectors.toSet());

        List<TaskOccurrence> created = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            if (rule.occursOn(day) && !existing.contains(day)) {
                TaskOccurrence o = new TaskOccurrence();
                o.setTaskId(task.getId());
                o.setOccurrenceDate(day);
                o.setTargetValue(task.getTargetValue());
                created.add(o);
            }
        }
        occurrences.saveAll(created);
        return created.size();
    }

    @Transactional
    public void refresh() {
        LocalDate today = LocalDate.now();
        int missed = occurrences.updateStatusBefore(OccurrenceStatus.PENDING, OccurrenceStatus.MISSED, today);

        int created = 0;
        for (Task task : tasks.findByActiveTrueAndRecurrenceRuleIsNotNull()) {
            try {
                created += generateFor(task, today);
            } catch (IllegalArgumentException e) {
                log.warn("Tarea {} con repetición inválida: {}", task.getId(), e.getMessage());
            }
        }
        log.info("Recurrencias: {} ocurrencias nuevas, {} marcadas como falladas", created, missed);
    }
}