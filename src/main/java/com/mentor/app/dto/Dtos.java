package com.mentor.app.dto;

import com.mentor.app.domain.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public final class Dtos {
    private Dtos() { }

    // ----- Goals -----
    public record GoalRequest(
            @NotBlank @Size(max = 200) String title,
            String description,
            LocalDate targetDate) { }

    public record GoalResponse(Long id, String title, String description, LocalDate targetDate) {
        public static GoalResponse from(Goal g) {
            return new GoalResponse(g.getId(), g.getTitle(), g.getDescription(), g.getTargetDate());
        }
    }

    // ----- Tasks -----
    public record TaskRequest(
            Long goalId,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 50) String unit,
            @NotNull @Positive BigDecimal targetValue,
            @Size(max = 255) String recurrenceRule,
            LocalDate startDate,
            LocalDate endDate,
            Boolean active) { }

    public record TaskResponse(
            Long id, Long goalId, String title, String unit, BigDecimal targetValue,
            String recurrenceRule, LocalDate startDate, LocalDate endDate, boolean active) {
        public static TaskResponse from(Task t) {
            return new TaskResponse(t.getId(), t.getGoalId(), t.getTitle(), t.getUnit(),
                    t.getTargetValue(), t.getRecurrenceRule(), t.getStartDate(), t.getEndDate(), t.isActive());
        }
    }

    // ----- Occurrences / progress -----
    public record OccurrenceResponse(
            Long id, Long taskId, String taskTitle, String unit, LocalDate date,
            BigDecimal targetValue, BigDecimal currentValue, double progress, OccurrenceStatus status) {
        public static OccurrenceResponse from(TaskOccurrence o, Task t) {
            double progress = o.getTargetValue().signum() == 0 ? 0
                    : o.getCurrentValue().divide(o.getTargetValue(), 4, RoundingMode.HALF_UP).doubleValue();
            return new OccurrenceResponse(o.getId(), o.getTaskId(), t.getTitle(), t.getUnit(),
                    o.getOccurrenceDate(), o.getTargetValue(), o.getCurrentValue(), progress, o.getStatus());
        }
    }

    // amount puede ser negativo para corregir un error
    public record ProgressRequest(@NotNull BigDecimal amount, @Size(max = 255) String note) { }
}