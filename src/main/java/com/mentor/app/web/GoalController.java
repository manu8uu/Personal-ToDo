package com.mentor.app.web;

import com.mentor.app.dao.GoalDao;
import com.mentor.app.domain.Goal;
import com.mentor.app.dto.Dtos.GoalRequest;
import com.mentor.app.dto.Dtos.GoalResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {
    private final GoalDao goals;

    @GetMapping
    public List<GoalResponse> list() {
        return goals.findAll(Sort.by("id")).stream().map(GoalResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@Valid @RequestBody GoalRequest r) {
        Goal g = new Goal();
        fill(g, r);
        return GoalResponse.from(goals.save(g));
    }

    @PutMapping("/{id}")
    @Transactional
    public GoalResponse update(@PathVariable Long id, @Valid @RequestBody GoalRequest r) {
        Goal g = goals.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Objetivo no encontrado"));
        fill(g, r);
        return GoalResponse.from(g);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!goals.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Objetivo no encontrado");
        }
        goals.deleteById(id);
    }

    private void fill(Goal g, GoalRequest r) {
        g.setTitle(r.title().trim());
        g.setDescription(r.description());
        g.setTargetDate(r.targetDate());
    }
}