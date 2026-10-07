package com.mentor.app.service;

import com.mentor.app.dao.ProgressEntryDao;
import com.mentor.app.dao.TaskDao;
import com.mentor.app.dao.TaskOccurrenceDao;
import com.mentor.app.domain.OccurrenceStatus;
import com.mentor.app.domain.ProgressEntry;
import com.mentor.app.domain.Task;
import com.mentor.app.domain.TaskOccurrence;
import com.mentor.app.dto.Dtos.OccurrenceResponse;
import com.mentor.app.dto.Dtos.ProgressRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OccurrenceService {
    private final TaskOccurrenceDao occurrences;
    private final TaskDao tasks;
    private final ProgressEntryDao progress;

    @Transactional(readOnly = true)
    public List<OccurrenceResponse> list(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'to' no puede ser anterior a 'from'");
        }
        return toResponses(occurrences.findByOccurrenceDateBetweenOrderByOccurrenceDateAscIdAsc(from, to));
    }

    @Transactional
    public OccurrenceResponse addProgress(Long occurrenceId, ProgressRequest r) {
        if (r.amount().signum() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount no puede ser 0");
        }
        TaskOccurrence o = occurrences.findById(occurrenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ocurrencia no encontrada"));

        ProgressEntry entry = new ProgressEntry();
        entry.setOccurrenceId(o.getId());
        entry.setAmount(r.amount());
        entry.setNote(r.note());
        progress.save(entry);

        BigDecimal newValue = o.getCurrentValue().add(r.amount()).max(BigDecimal.ZERO);
        o.setCurrentValue(newValue);
        o.setStatus(newValue.compareTo(o.getTargetValue()) >= 0 ? OccurrenceStatus.DONE : OccurrenceStatus.PENDING);
        return toResponses(List.of(o)).get(0);
    }

    private List<OccurrenceResponse> toResponses(List<TaskOccurrence> list) {
        Set<Long> ids = list.stream().map(TaskOccurrence::getTaskId).collect(Collectors.toSet());
        Map<Long, Task> byId = tasks.findAllById(ids).stream().collect(Collectors.toMap(Task::getId, t -> t));
        return list.stream().map(o -> OccurrenceResponse.from(o, byId.get(o.getTaskId()))).toList();
    }
}