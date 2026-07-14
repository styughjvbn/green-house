package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.WorkRecord;
import com.greenhouse.backend.work.domain.WorkRecordStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkRecordRepository extends JpaRepository<WorkRecord, Long> {

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

	@Query("""
			select w from WorkRecord w
			where (:targetType is null or w.targetType = :targetType)
				and (:targetId is null or w.targetId = :targetId)
				and (:workType is null or w.workType = :workType)
				and (:from is null or w.workDate >= :from)
				and (:to is null or w.workDate <= :to)
				and (:includeCanceled = true or w.status <> :canceledStatus)
			order by w.workDate desc, w.id desc
			""")
	List<WorkRecord> search(
			@Param("targetType") String targetType,
			@Param("targetId") Long targetId,
			@Param("workType") String workType,
			@Param("from") LocalDate from,
			@Param("to") LocalDate to,
			@Param("includeCanceled") boolean includeCanceled,
			@Param("canceledStatus") WorkRecordStatus canceledStatus);

	@Query("""
			select w.targetId as targetId, max(w.workDate) as latestWorkDate
			from WorkRecord w
			where w.targetType = :targetType
				and w.targetId in :targetIds
				and w.status = :status
			group by w.targetId
			""")
	List<WorkDateRow> findLatestWorkDates(
			@Param("targetType") String targetType,
			@Param("targetIds") Collection<Long> targetIds,
			@Param("status") WorkRecordStatus status);

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
