package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.WorkRecord;
import com.greenhouse.backend.work.domain.WorkRecordStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkRecordRepository extends JpaRepository<WorkRecord, Long>, WorkRecordRepositoryCustom {

	Optional<WorkRecord> findTopByTargetTypeAndTargetIdAndWorkTypeOrderByWorkDateDescIdDesc(
			String targetType,
			Long targetId,
			String workType);

	default List<WorkRecord> search(
			String targetType,
			Long targetId,
			String workType,
			LocalDate from,
			LocalDate to) {
		return search(targetType, targetId, workType, from, to, false, WorkRecordStatus.CANCELED);
	}

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			delete from work_records
			where details ->> 'inboundRecordId' = cast(:inboundRecordId as text)
				and work_type_id in (
					select id from work_types where code = :workTypeCode
				)
			""", nativeQuery = true)
	int deleteByDetailsInboundRecordIdAndWorkTypeCode(
			@Param("inboundRecordId") Long inboundRecordId,
			@Param("workTypeCode") String workTypeCode);
}
