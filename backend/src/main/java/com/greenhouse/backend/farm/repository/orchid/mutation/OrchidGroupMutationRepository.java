package com.greenhouse.backend.farm.repository.orchid.mutation;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrchidGroupMutationRepository extends JpaRepository<OrchidGroupMutation, Long> {

  @Query(
      """
      select new com.greenhouse.backend.farm.repository.orchid.mutation.CorrectionMutationReconciliationRow(
          m.id, m.correlationId, m.sourceDomain, m.mutationType, m.sourceType, m.sourceReferenceId)
      from OrchidGroupMutation m where m.id in :ids
      """)
  List<CorrectionMutationReconciliationRow> findCorrectionReconciliationRowsByIdIn(
      Collection<Long> ids);

  Optional<OrchidGroupMutation>
      findBySourceDomainAndSourceTypeAndSourceReferenceIdAndSourceOperationKey(
          OrchidGroupMutationSourceDomain sourceDomain,
          String sourceType,
          String sourceReferenceId,
          String sourceOperationKey);

  @Query(
      "select count(m) from OrchidGroupMutation m where not exists "
          + "(select e.id from OrchidGroupMutationEntry e where e.mutation = m)")
  long countWithoutEntries();

  @Query(
      """
			select mutation from OrchidGroupMutation mutation
			where (:mutationType is null or mutation.mutationType = :mutationType)
			  and (:sourceDomain is null or mutation.sourceDomain = :sourceDomain)
			  and (:orchidGroupId is null or exists (
			      select entry.id from OrchidGroupMutationEntry entry
			      where entry.mutation = mutation and entry.orchidGroupId = :orchidGroupId
			  ))
			""")
  Page<OrchidGroupMutation> search(
      @Param("orchidGroupId") Long orchidGroupId,
      @Param("mutationType") OrchidGroupMutationType mutationType,
      @Param("sourceDomain") OrchidGroupMutationSourceDomain sourceDomain,
      Pageable pageable);
}
