package com.greenhouse.backend.farm.application.structure;

import com.greenhouse.backend.audit.application.AuditEventWriter;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneCapacity;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class BedPlacementAuditSupport {
	private final AuditEventWriter auditWriter;

	public BedPlacementAuditSupport(AuditEventWriter auditWriter) {
		this.auditWriter = auditWriter;
	}

	public Map<String, Object> snapshot(BedZone zone) {
		var data = new LinkedHashMap<String, Object>();
		data.put("zoneId", zone.getId());
		data.put("zoneSide", zone.getSide());
		data.put("capacities", zone.getCapacities().stream()
				.sorted(Comparator.comparing(BedZoneCapacity::getCapacityMode)
						.thenComparing(BedZoneCapacity::getPlacementType)
						.thenComparing(capacity -> capacity.getPotSize() == null ? "" : capacity.getPotSize()))
				.map(capacity -> {
					var rule = new LinkedHashMap<String, Object>();
					rule.put("placementType", capacity.getPlacementType());
					rule.put("potSize", capacity.getPotSize());
					rule.put("capacityMode", capacity.getCapacityMode());
					rule.put("unitSpan", capacity.getUnitSpan());
					rule.put("capacityValue", capacity.getCapacityValue());
					rule.put("allowed", capacity.getAllowed());
					rule.put("memo", capacity.getMemo());
					return rule;
				}).toList());
		return data;
	}

	public Long record(BedZone zone, Map<String, Object> before, Map<String, Object> after) {
		var bed = zone.getPhysicalBed();
		return auditWriter.record(AuditAction.UPDATED, AuditSource.FARM_STRUCTURE_MANAGEMENT, "BED_ZONE",
				zone.getId(), bed.getHouse().getId(), bed.getId(), zone.getId(), null, before, after, Map.of());
	}
}
