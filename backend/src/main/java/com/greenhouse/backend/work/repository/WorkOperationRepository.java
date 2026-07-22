package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.operation.WorkOperation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOperationRepository
		extends JpaRepository<WorkOperation, Long>, WorkOperationRepositoryCustom {
}
