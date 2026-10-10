package com.mentor.app.service;

import com.mentor.app.dao.GoalDao;
import com.mentor.app.dao.TaskDao;
import com.mentor.app.dao.TaskOccurrenceDao;
import com.mentor.app.domain.OccurrenceStatus;
import com.mentor.app.domain.Task;
import com.mentor.app.domain.TaskOccurrence;
import com.mentor.app.dto.Dtos.TaskRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskDao tasks;
    private final GoalDao goals;
    private final TaskOccurrenceDao occurrences;
    private final RecurrenceService recurrence;

    @Transactional(readOnly = true)
    public List<Task> list() {
        return tasks.findAllByOrderByIdDesc();
    }

    @Transactional
    public Task create(TaskRequest r) {
        Task t = new Task();
        apply(t, r);
        t = tasks.save(t);

        if (t.getRecurrenceRule() == null) {
            // tarea ocasional: una sola ocurrencia
            TaskOccurrence o = new TaskOccurrence();
            o.setTaskId(t.getId());
            o.setOccurrenceDate(t.getStartDate());
            o.setTargetValue(t.getTargetValue());
            occurrences.save(o);
        } else {
            recurrence.generateFor(t, LocalDate.now());
        }
        return t;
    }

    // edita la plantilla; las ocurrencias que ya existen no se tocan
    @Transactional
    public Task update(Long id, TaskRequest r) {
        Task t = find(id);
        apply(t, r);
        if (t.getRecurrenceRule() != null) {
            recurrence.generateFor(t, LocalDate.now());
        }
        return t;
    }

    // scope "all": borra la tarea con todo su historial
    // scope "future": la elimina desde hoy y conserva los días anteriores
    @Transactional
    public void delete(Long id, String scope) {
        Task t = find(id);
        switch (scope.toLowerCase()) {
            case "all" -> tasks.delete(t);
            case "future" -> stopFromToday(t);
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "scope debe ser 'all' o 'future'");
        }
    }

    private void stopFromToday(Task t) {
        LocalDate today = LocalDate.now();
        occurrences.deleteByTaskIdAndOccurrenceDateGreaterThanEqual(t.getId(), today);

        // si no queda nada visible del pasado, se borra la tarea entera
        if (!occurrences.existsByTaskIdAndStatusNot(t.getId(), OccurrenceStatus.SKIPPED)) {
            tasks.delete(t);
            return;
        }
        t.setEndDate(today.minusDays(1));
    }

    private Task find(Long id) {
        return tasks.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarea no encontrada"));
    }

    private void apply(Task t, TaskRequest r) {
        if (r.goalId() != null && !goals.existsById(r.goalId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El objetivo indicado no existe");
        }
        LocalDate start = r.startDate() != null ? r.startDate() : LocalDate.now();
        if (r.endDate() != null && r.endDate().isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate no puede ser anterior a startDate");
        }

        String rule = r.recurrenceRule() == null || r.recurrenceRule().isBlank()
                ? null
                : r.recurrenceRule().trim().toUpperCase();
        if (rule != null) {
            try {
                Recurrence.parse(rule, start);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            }
        }

        t.setGoalId(r.goalId());
        t.setTitle(r.title().trim());
        t.setUnit(r.unit().trim());
        t.setTargetValue(r.targetValue());
        t.setRecurrenceRule(rule);
        t.setStartDate(start);
        t.setEndDate(r.endDate());
        if (r.active() != null) {
            t.setActive(r.active());
        }
    }
}