package com.mentor.app.web;

import com.mentor.app.dto.Dtos.StatsResponse;
import com.mentor.app.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {
    private final StatsService service;

    // weeks = semanas que cubre el mapa de actividad (entre 4 y 52)
    @GetMapping
    public StatsResponse stats(@RequestParam(defaultValue = "17") int weeks) {
        return service.compute(Math.min(Math.max(weeks, 4), 52));
    }
}