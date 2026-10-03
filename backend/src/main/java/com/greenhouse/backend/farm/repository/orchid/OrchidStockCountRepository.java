package com.greenhouse.backend.farm.repository.orchid;

import com.greenhouse.backend.farm.domain.orchid.OrchidStockCount;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrchidStockCountRepository extends JpaRepository<OrchidStockCount, String> {

	@Modifying
	@Query(value = "INSERT INTO orchid_stock_counts (request_key, request_fingerprint, orchid_group_id, recorded_at, business_date, worker, reason, memo) VALUES (:key, :fingerprint, :groupId, :now, :date, :worker, :reason, :memo) ON CONFLICT DO NOTHING",
			nativeQuery = true)
	int claim(@Param("key") String key, @Param("fingerprint") String fingerprint, @Param("groupId") Long groupId,
			@Param("now") LocalDateTime now, @Param("date") LocalDate date, @Param("worker") String worker,
			@Param("reason") String reason, @Param("memo") String memo);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from OrchidStockCount c where c.requestKey = :key")
	OrchidStockCount findForUpdate(@Param("key") String key);

	Page<OrchidStockCount> findByOrchidGroupIdAndMutationIdIsNotNullOrderByRecordedAtDescRequestKeyDesc(Long groupId,
			Pageable pageable);

	boolean existsByOrchidGroupIdInAndMutationIdIsNotNull(Set<Long> ids);

	@Query("""
			select count(c) > 0 from OrchidStockCount c, OrchidGroupMutationEntry counted
			where c.orchidGroupId in :ids and counted.mutation.id = c.mutationId
			and counted.orchidGroupId = c.orchidGroupId
			and counted.stateRevisionAfter > (
			  select max(original.stateRevisionAfter) from OrchidGroupMutationEntry original
			  where original.mutation.id in :mutations and original.orchidGroupId = c.orchidGroupId
			)
			""")
	boolean existsAfterMutations(@Param("ids") Set<Long> ids, @Param("mutations") List<Long> mutations);

}
