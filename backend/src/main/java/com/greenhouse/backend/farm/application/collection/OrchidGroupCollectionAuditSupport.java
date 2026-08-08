package com.greenhouse.backend.farm.application.collection;

import com.greenhouse.backend.audit.application.AuditEventWriter;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.farm.domain.collection.OrchidGroupCollection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OrchidGroupCollectionAuditSupport {
	private final AuditEventWriter auditWriter;

	public OrchidGroupCollectionAuditSupport(AuditEventWriter auditWriter) {
		this.auditWriter = auditWriter;
	}

	public Map<String, Object> snapshot(OrchidGroupCollection collection, List<Long> memberIds) {
		var data = new LinkedHashMap<String, Object>();
		data.put("name", collection.getName());
		data.put("description", collection.getDescription());
		data.put("purpose", collection.getPurpose());
		data.put("status", collection.getStatus());
		data.put("createdBy", collection.getCreatedBy());
		data.put("memberIds", memberIds.stream().sorted().toList());
		return data;
	}

	public Long record(AuditAction action, OrchidGroupCollection collection,
			Map<String, Object> before, Map<String, Object> after, Map<String, Object> context) {
		return auditWriter.record(action, AuditSource.ORCHID_GROUP_COLLECTION, "ORCHID_GROUP_COLLECTION",
				collection.getId(), before, after, context);
	}
}
