package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.effect.WorkAppliedEffect;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkAppliedEffectRepository extends JpaRepository<WorkAppliedEffect, Long> {

	long countByWorkOperationIdAndTargetId(Long workOperationId, Long targetId);

	@EntityGraph(attributePaths = "target")
	List<WorkAppliedEffect> findByWorkOperationIdOrderByIdAsc(Long workOperationId);

	Optional<WorkAppliedEffect> findByWorkOperationIdAndEffectKey(Long workOperationId, String effectKey);

	@EntityGraph(attributePaths = {"workOperation", "workOperation.workType", "target"})
	@Query("""
			select effect from WorkAppliedEffect effect
			join effect.target target
			where target.inboundRecordId = :inboundRecordId
			  and effect.effectKey = :effectKey
			""")
	Optional<WorkAppliedEffect> findInboundPottingEffect(
			@Param("inboundRecordId") Long inboundRecordId,
			@Param("effectKey") String effectKey);

	@EntityGraph(attributePaths = {"workOperation", "workOperation.workType", "target"})
	@Query("""
			select effect from WorkAppliedEffect effect
			join effect.target target
			where target.inboundRecordId in :inboundRecordIds
			  and effect.effectKey in :effectKeys
			""")
	List<WorkAppliedEffect> findInboundPottingEffects(
			@Param("inboundRecordIds") Collection<Long> inboundRecordIds,
			@Param("effectKeys") Collection<String> effectKeys);

	@EntityGraph(attributePaths = "workOperation")
	List<WorkAppliedEffect> findByWorkOperationIdInAndEffectKey(Collection<Long> workOperationIds, String effectKey);

	@Query("""
			select effect.id
			from WorkAppliedEffect effect
			where (effect.mutationId is null and effect.correlationId is not null)
			   or (effect.mutationId is not null and effect.correlationId is null)
			order by effect.id
			""")
	List<Long> findIdsWithIncompleteMutationLink();

}
