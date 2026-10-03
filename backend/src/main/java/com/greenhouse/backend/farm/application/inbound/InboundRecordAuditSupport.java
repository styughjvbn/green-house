package com.greenhouse.backend.farm.application.inbound;

import com.greenhouse.backend.audit.application.AuditEvent;
import com.greenhouse.backend.audit.application.AuditEventWriter;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.farm.domain.inbound.InboundRecord;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InboundRecordAuditSupport {

  private final AuditEventWriter auditWriter;

  public Map<String, Object> snapshot(InboundRecord record) {
    var data = new LinkedHashMap<String, Object>();
    data.put("inboundDate", record.getInboundDate());
    data.put("inboundType", record.getInboundType());
    data.put("varietyId", record.getVariety().getId());
    data.put("status", record.getStatus());
    data.put("estimatedQuantity", record.getEstimatedQuantity());
    data.put("tempLocation", record.getTempLocation());
    data.put("pottingDueDate", record.getPottingDueDate());
    data.put("worker", record.getWorker());
    data.put("memo", record.getMemo());
    return data;
  }

  public Long record(
      AuditAction action,
      InboundRecord record,
      Map<String, Object> before,
      Map<String, Object> after) {
    return auditWriter.record(
        action,
        AuditSource.INBOUND_MANAGEMENT,
        new AuditEvent.Target(
            "INBOUND_RECORD", record.getId(), null, null, null, record.getVariety().getId()),
        before,
        after,
        Map.of());
  }
}
