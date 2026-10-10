package com.mentor.app.web;

import com.mentor.app.dto.Dtos.OccurrenceResponse;
import com.mentor.app.dto.Dtos.ProgressRequest;
import com.mentor.app.service.OccurrenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/occurrences")
@RequiredArgsConstructor
public class OccurrenceController {
    private final OccurrenceService service;

    // sin parámetros devuelve las de hoy; fechas en formato 2026-10-07
    @GetMapping
    public List<OccurrenceResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from != null ? from : LocalDate.now();
        LocalDate end = to != null ? to : start;
        return service.list(start, end);
    }

    @PostMapping("/{id}/progress")
    public OccurrenceResponse addProgress(@PathVariable Long id, @Valid @RequestBody ProgressRequest r) {
        return service.addProgress(id, r);
    }

    // omite ese día; si la tarea es ocasional, la elimina
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void skip(@PathVariable Long id) {
        service.skip(id);
    }
}