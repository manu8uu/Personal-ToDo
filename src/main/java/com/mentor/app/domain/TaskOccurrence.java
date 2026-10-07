package com.mentor.app.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "task_occurrence")
@Getter @Setter @NoArgsConstructor
public class TaskOccurrence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long taskId;

    @Column(nullable = false)
    private LocalDate occurrenceDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal targetValue;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal currentValue = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OccurrenceStatus status = OccurrenceStatus.PENDING;
}