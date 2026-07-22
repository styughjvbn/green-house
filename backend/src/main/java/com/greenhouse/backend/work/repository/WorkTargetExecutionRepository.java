package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.target.WorkTargetExecution;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkTargetExecutionRepository
		extends JpaRepository<WorkTargetExecution, Long>, WorkTargetExecutionRepositoryCustom {
}
