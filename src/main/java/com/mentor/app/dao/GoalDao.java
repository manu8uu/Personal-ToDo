package com.mentor.app.dao;

import com.mentor.app.domain.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalDao extends JpaRepository<Goal, Long> { }