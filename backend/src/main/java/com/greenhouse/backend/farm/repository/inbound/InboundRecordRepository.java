package com.greenhouse.backend.farm.repository.inbound;

import com.greenhouse.backend.farm.domain.inbound.InboundRecord;
import com.greenhouse.backend.farm.domain.inbound.InboundStatus;
import com.greenhouse.backend.farm.domain.inbound.InboundType;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InboundRecordRepository extends JpaRepository<InboundRecord, Long> {

	@EntityGraph(attributePaths = { "variety", "createdOrchidGroups" })
	Optional<InboundRecord> findWithDetailsById(Long id);

	@EntityGraph(attributePaths = { "variety", "createdOrchidGroups" })
	List<InboundRecord> findByIdIn(Collection<Long> ids);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select distinct record from InboundRecord record
			join fetch record.variety
			left join fetch record.createdOrchidGroups
			where record.id in :ids
			order by record.id asc
			""")
	List<InboundRecord> findAllForUpdateByIdIn(@Param("ids") Collection<Long> ids);

	@EntityGraph(attributePaths = { "variety", "createdOrchidGroups" })
	List<InboundRecord> findByInboundTypeAndStatusInOrderByPottingDueDateAscIdAsc(
			InboundType inboundType, Collection<InboundStatus> statuses);

	@Query("""
			select record from InboundRecord record
			join record.variety variety
			where (:from is null or record.inboundDate >= :from)
			  and (:to is null or record.inboundDate <= :to)
			  and (:inboundType is null or record.inboundType = :inboundType)
			  and (:status is null or record.status = :status)
			  and (:varietyKeyword = '' or lower(variety.name) like lower(concat('%', :varietyKeyword, '%')))
			order by record.inboundDate desc, record.id desc
			""")
	@EntityGraph(attributePaths = { "variety" })
	Page<InboundRecord> search(@Param("from") LocalDate from, @Param("to") LocalDate to,
			@Param("inboundType") InboundType inboundType, @Param("status") InboundStatus status,
			@Param("varietyKeyword") String varietyKeyword, Pageable pageable);

	boolean existsByVarietyId(Long varietyId);

	@Query("""
			select max(record.inboundDate)
			from InboundRecord record
			where record.variety.id = :varietyId
			""")
	LocalDate findLatestInboundDateByVarietyId(@Param("varietyId") Long varietyId);

	@Query("""
			select record.variety.id, max(record.inboundDate)
			from InboundRecord record
			where record.variety.id in :varietyIds
			group by record.variety.id
			""")
	List<Object[]> findLatestInboundDatesByVarietyIds(@Param("varietyIds") Collection<Long> varietyIds);

}
