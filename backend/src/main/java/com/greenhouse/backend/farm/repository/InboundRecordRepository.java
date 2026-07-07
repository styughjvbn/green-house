package com.greenhouse.backend.farm.repository;

import com.greenhouse.backend.farm.domain.InboundRecord;
import com.greenhouse.backend.farm.domain.InboundStatus;
import com.greenhouse.backend.farm.domain.InboundType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InboundRecordRepository extends JpaRepository<InboundRecord, Long> {

	@EntityGraph(attributePaths = {
		"variety",
		"bedZone",
		"bedZone.physicalBed",
		"bedZone.physicalBed.house",
		"createdOrchidGroup"
	})
	Optional<InboundRecord> findWithDetailsById(Long id);

	@Query("""
		select record from InboundRecord record
		join record.variety variety
		left join record.bedZone bedZone
		left join bedZone.physicalBed bed
		left join bed.house house
		where (:from is null or record.inboundDate >= :from)
		  and (:to is null or record.inboundDate <= :to)
		  and (:inboundType is null or record.inboundType = :inboundType)
		  and (:status is null or record.status = :status)
		  and (:varietyKeyword = '' or lower(variety.name) like lower(concat('%', :varietyKeyword, '%')))
		order by record.inboundDate desc, record.id desc
		""")
	Page<InboundRecord> search(
		@Param("from") LocalDate from,
		@Param("to") LocalDate to,
		@Param("inboundType") InboundType inboundType,
		@Param("status") InboundStatus status,
		@Param("varietyKeyword") String varietyKeyword,
		Pageable pageable
	);

	boolean existsByVarietyId(Long varietyId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		update InboundRecord record
		set record.createdOrchidGroup = null
		where record.createdOrchidGroup.id = :orchidGroupId
		""")
	int clearCreatedOrchidGroup(@Param("orchidGroupId") Long orchidGroupId);

	@Query("""
		select max(record.inboundDate)
		from InboundRecord record
		where record.variety.id = :varietyId
		""")
	LocalDate findLatestInboundDateByVarietyId(@Param("varietyId") Long varietyId);
}
