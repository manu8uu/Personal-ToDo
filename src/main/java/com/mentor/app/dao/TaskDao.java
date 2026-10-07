package com.mentor.app.dao;

import com.mentor.app.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskDao extends JpaRepository<Task, Long> {
    List<Task> findAllByOrderByIdDesc();
}