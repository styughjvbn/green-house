package com.greenhouse.backend.farm.inbound.integration;

import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.farm.application.inbound.InboundRecordAuditSupport;
import com.greenhouse.backend.farm.application.inbound.InboundRecordFinder;
import com.greenhouse.backend.work.api.operation.InboundWorkOperationLifecycleApi;
import com.greenhouse.backend.work.spi.operation.InboundPottingVoidPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class FarmInboundPottingVoidAdapter implements InboundPottingVoidPort {

  private final InboundWorkOperationLifecycleApi lifecycleService;
  private final InboundRecordFinder finder;
  private final InboundRecordAuditSupport auditSupport;

  @Override
  public Long voidPotting(Long inboundRecordId, String requestKey, String reason) {
    lifecycleService.lockForInboundChange(inboundRecordId);
    var inboundRecord = finder.findForUpdate(inboundRecordId);
    inboundRecord.requirePottingVoidAllowed();
    var before = auditSupport.snapshot(inboundRecord);
    Long operationId =
        lifecycleService.voidPottingForInboundRecord(inboundRecordId, requestKey, reason);
    auditSupport.record(
        AuditAction.UPDATED, inboundRecord, before, auditSupport.snapshot(inboundRecord));
    return operationId;
  }
}
