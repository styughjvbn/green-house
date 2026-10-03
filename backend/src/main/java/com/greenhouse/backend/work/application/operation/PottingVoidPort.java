package com.greenhouse.backend.work.application.operation;

import java.time.LocalDate;
import java.util.List;

public interface PottingVoidPort {

  Inspection inspect(Long workOperationId, List<Effect> effects);

  Inspection inspectForUpdate(Long workOperationId, List<Effect> effects);

  Long compensate(
      Long workOperationId,
      String requestKey,
      List<Effect> effects,
      LocalDate businessDate,
      String reason,
      boolean reopenInboundRecords);

  record Effect(Long inboundRecordId, Long mutationId) {}

  record Inspection(
      List<StructureChangeVoidPort.OrchidGroupSummary> resultOrchidGroups,
      List<StructureChangeVoidPort.Blocker> blockers) {
    public Inspection {
      resultOrchidGroups = List.copyOf(resultOrchidGroups);
      blockers = List.copyOf(blockers);
    }
  }
}
