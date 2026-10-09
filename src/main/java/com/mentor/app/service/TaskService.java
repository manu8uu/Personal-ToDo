package com.mentor.app.service;

import com.mentor.app.dao.GoalDao;
import com.mentor.app.dao.TaskDao;
import com.mentor.app.dao.TaskOccurrenceDao;
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
        Task t = tasks.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarea no encontrada"));
        apply(t, r);
        if (t.getRecurrenceRule() != null) {
            recurrence.generateFor(t, LocalDate.now());
        }
        return t;
    }

    @Transactional
    public void delete(Long id) {
        if (!tasks.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarea no encontrada");
        }
        tasks.deleteById(id);
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