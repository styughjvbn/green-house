package com.greenhouse.backend.work.application.operation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface WorkOperationMutationGraphPort {

  Fragment load(Collection<Long> mutationIds, boolean expandLineage, int depth, int maxNodes);

  record Fragment(
      boolean truncated, List<MutationNode> mutations, List<StateNode> states, List<Edge> edges) {
    public static Fragment empty() {
      return new Fragment(false, List.of(), List.of(), List.of());
    }
  }

  record MutationNode(Long id, String type, LocalDate effectiveBusinessDate, Instant occurredAt) {}

  record StateNode(String id, Long orchidGroupId, Long stateRevision, State state) {}

  record State(
      Integer quantity,
      Integer reservedQuantity,
      String status,
      Long varietyId,
      String genus,
      String varietyName,
      Integer ageYear,
      String potSizeCode,
      Long bedZoneId,
      Integer houseNumber,
      Integer physicalBedNumber,
      String bedZoneName,
      BigDecimal startPosition,
      BigDecimal endPosition) {}

  record Edge(
      String id, String sourceNodeId, String targetNodeId, String type, String relationType) {}
}
