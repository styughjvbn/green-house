package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.WorkRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkRecordRepository extends JpaRepository<WorkRecord, Long> {

	Optional<WorkRecord> findTopByTargetTypeAndTargetIdAndWorkTypeOrderByWorkDateDescIdDesc(
		String targetType,
		Long targetId,
		String workType
	);
}
