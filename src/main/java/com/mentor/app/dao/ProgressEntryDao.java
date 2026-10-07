package com.mentor.app.dao;

import com.mentor.app.domain.ProgressEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgressEntryDao extends JpaRepository<ProgressEntry, Long> { }