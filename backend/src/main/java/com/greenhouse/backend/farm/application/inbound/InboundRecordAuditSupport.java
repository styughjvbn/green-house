package com.greenhouse.backend.farm.application.inbound;

import com.greenhouse.backend.audit.application.AuditEventWriter;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.farm.domain.inbound.InboundRecord;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class InboundRecordAuditSupport {
	private final AuditEventWriter auditWriter;

	public InboundRecordAuditSupport(AuditEventWriter auditWriter) {
		this.auditWriter = auditWriter;
	}

	public Map<String, Object> snapshot(InboundRecord record) {
		var data = new LinkedHashMap<String, Object>();
		data.put("inboundDate", record.getInboundDate());
		data.put("inboundType", record.getInboundType());
		data.put("varietyId", record.getVariety().getId());
		data.put("status", record.getStatus());
		data.put("bottleCount", record.getBottleCount());
		data.put("estimatedQuantity", record.getEstimatedQuantity());
		data.put("actualQuantity", record.getActualQuantity());
		data.put("tempLocation", record.getTempLocation());
		data.put("pottingDueDate", record.getPottingDueDate());
		data.put("pottingDate", record.getPottingDate());
		data.put("potSize", record.getPotSize());
		data.put("ageYear", record.getAgeYear());
		data.put("growthStage", record.getGrowthStage());
		data.put("placementType", record.getPlacementType());
		data.put("trayCount", record.getTrayCount());
		data.put("bedZoneId", record.getBedZone() == null ? null : record.getBedZone().getId());
		data.put("createdOrchidGroupId",
				record.getCreatedOrchidGroup() == null ? null : record.getCreatedOrchidGroup().getId());
		data.put("worker", record.getWorker());
		data.put("memo", record.getMemo());
		return data;
	}

	public Long record(AuditAction action, InboundRecord record,
			Map<String, Object> before, Map<String, Object> after) {
		Long zoneId = record.getBedZone() == null ? null : record.getBedZone().getId();
		Long bedId = record.getBedZone() == null ? null : record.getBedZone().getPhysicalBed().getId();
		Long houseId = record.getBedZone() == null ? null
				: record.getBedZone().getPhysicalBed().getHouse().getId();
		return auditWriter.record(action, AuditSource.INBOUND_MANAGEMENT, "INBOUND_RECORD", record.getId(),
				houseId, bedId, zoneId, record.getVariety().getId(), before, after, Map.of());
	}
}
