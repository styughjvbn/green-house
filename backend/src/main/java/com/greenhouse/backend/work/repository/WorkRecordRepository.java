package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.WorkRecord;
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
		String workType
	);

	@Query("""
		select w from WorkRecord w
		where (:targetType is null or w.targetType = :targetType)
			and (:targetId is null or w.targetId = :targetId)
			and (:workType is null or w.workType = :workType)
			and (:from is null or w.workDate >= :from)
			and (:to is null or w.workDate <= :to)
		order by w.workDate desc, w.id desc
		""")
	List<WorkRecord> search(
		@Param("targetType") String targetType,
		@Param("targetId") Long targetId,
		@Param("workType") String workType,
		@Param("from") LocalDate from,
		@Param("to") LocalDate to
	);

	@Query("""
		select w.targetId as targetId, max(w.workDate) as latestWorkDate
		from WorkRecord w
		where w.targetType = :targetType
			and w.targetId in :targetIds
		group by w.targetId
		""")
	List<WorkDateRow> findLatestWorkDates(
		@Param("targetType") String targetType,
		@Param("targetIds") Collection<Long> targetIds
	);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		delete from WorkRecord w
		where w.targetType = :targetType
			and w.targetId = :targetId
			and w.workTypeRef.code = :workTypeCode
		""")
	int deleteByTargetTypeAndTargetIdAndWorkTypeCode(
		@Param("targetType") String targetType,
		@Param("targetId") Long targetId,
		@Param("workTypeCode") String workTypeCode
	);
}
