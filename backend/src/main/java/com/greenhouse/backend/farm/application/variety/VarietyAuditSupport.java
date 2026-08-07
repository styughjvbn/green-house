package com.greenhouse.backend.farm.application.variety;

import com.greenhouse.backend.audit.application.AuditEventWriter;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.farm.domain.variety.Variety;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class VarietyAuditSupport {
	private final AuditEventWriter auditWriter;

	public VarietyAuditSupport(AuditEventWriter auditWriter) {
		this.auditWriter = auditWriter;
	}

	public Map<String, Object> snapshot(Variety variety) {
		var data = new LinkedHashMap<String, Object>();
		data.put("code", variety.getCode());
		data.put("genus", variety.getGenus());
		data.put("name", variety.getName());
		data.put("alias", variety.getAlias());
		data.put("defaultPotSize", variety.getDefaultPotSize());
		data.put("color", variety.getColor());
		data.put("saleEnabled", variety.isSaleEnabled());
		data.put("active", variety.isActive());
		data.put("description", variety.getDescription());
		data.put("memo", variety.getMemo());
		return data;
	}

	public Long record(AuditAction action, Variety variety, Map<String, Object> before, Map<String, Object> after) {
		return auditWriter.record(action, AuditSource.VARIETY_MANAGEMENT, "VARIETY", variety.getId(),
				null, null, null, variety.getId(), before, after, Map.of());
	}
}
