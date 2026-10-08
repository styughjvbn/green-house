package com.greenhouse.backend.work.api.operation;

import java.util.Collection;
import java.util.Set;

/** Work-owned lifecycle of operations linked to Farm inbound records. */
public interface InboundWorkOperationLifecycleApi {

  void lockForInboundChange(Long inboundRecordId);

  Set<Long> findInboundIdsWithUndoablePotting(Collection<Long> inboundIds);

  Long voidPottingForInboundRecord(Long inboundRecordId, String requestKey, String reason);

  void voidInboundRegistrationForCancellation(
      Long inboundRecordId, String requestKey, String reason);

  void cancelForInboundRecord(Long inboundRecordId);
}
