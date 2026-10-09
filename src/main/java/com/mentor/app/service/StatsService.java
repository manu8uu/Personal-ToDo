package com.mentor.app.service;

import com.mentor.app.dao.TaskOccurrenceDao;
import com.mentor.app.domain.OccurrenceStatus;
import com.mentor.app.domain.TaskOccurrence;
import com.mentor.app.dto.Dtos.DayStats;
import com.mentor.app.dto.Dtos.StatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class StatsService {
    private final TaskOccurrenceDao occurrences;

    @Transactional(readOnly = true)
    public StatsResponse compute(int weeks) {
        LocalDate today = LocalDate.now();
        List<TaskOccurrence> all =
                occurrences.findByOccurrenceDateLessThanEqualOrderByOccurrenceDateAsc(today);

        Map<LocalDate, List<TaskOccurrence>> grouped = new TreeMap<>();
        for (TaskOccurrence o : all) {
            grouped.computeIfAbsent(o.getOccurrenceDate(), d -> new ArrayList<>()).add(o);
        }
        List<DayStats> days = grouped.entrySet().stream()
                .map(e -> dayStats(e.getKey(), e.getValue()))
                .toList();

        // racha: días consecutivos con tareas en los que todo está completado
        int best = 0;
        int run = 0;
        for (DayStats d : days) {
            boolean perfect = d.done() == d.total();
            if (d.date().equals(today) && !perfect) {
                continue; // hoy todavía puede completarse
            }
            run = perfect ? run + 1 : 0;
            best = Math.max(best, run);
        }

        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate from = weekStart.minusWeeks(weeks - 1L);

        int weekDone = 0;
        int weekTotal = 0;
        int totalCompleted = 0;
        for (DayStats d : days) {
            totalCompleted += d.done();
            if (!d.date().isBefore(weekStart)) {
                weekDone += d.done();
                weekTotal += d.total();
            }
        }

        List<DayStats> window = days.stream().filter(d -> !d.date().isBefore(from)).toList();
        return new StatsResponse(run, best, weekDone, weekTotal, totalCompleted, from, window);
    }

    private static DayStats dayStats(LocalDate date, List<TaskOccurrence> list) {
        int done = 0;
        double sum = 0;
        for (TaskOccurrence o : list) {
            if (o.getStatus() == OccurrenceStatus.DONE) {
                done++;
            }
            sum += ratio(o);
        }
        double progress = Math.round(sum / list.size() * 1000) / 1000.0;
        return new DayStats(date, list.size(), done, progress);
    }

    private static double ratio(TaskOccurrence o) {
        if (o.getTargetValue().signum() == 0) {
            return 0;
        }
        double r = o.getCurrentValue().divide(o.getTargetValue(), 4, RoundingMode.HALF_UP).doubleValue();
        return Math.min(r, 1.0);
    }
}