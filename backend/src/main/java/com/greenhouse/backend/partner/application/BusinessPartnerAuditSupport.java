package com.greenhouse.backend.partner.application;

import com.greenhouse.backend.audit.application.AuditEventWriter;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class BusinessPartnerAuditSupport {
	private static final List<String> REDACTED_FIELDS = List.of("ownerName", "phone", "address", "memo");
	private final AuditEventWriter auditWriter;

	public BusinessPartnerAuditSupport(AuditEventWriter auditWriter) {
		this.auditWriter = auditWriter;
	}

	public Snapshot snapshot(BusinessPartner partner) {
		return new Snapshot(partner.getName(), partner.getPartnerType(), partner.getOwnerName(),
				partner.getPhone(), partner.getAddress(), partner.getMemo(), partner.isActive());
	}

	public void recordCreated(BusinessPartner partner) {
		record(AuditAction.CREATED, partner, null, snapshot(partner));
	}

	public void recordUpdated(BusinessPartner partner, Snapshot before) {
		record(AuditAction.UPDATED, partner, before, snapshot(partner));
	}

	private void record(AuditAction action, BusinessPartner partner, Snapshot before, Snapshot after) {
		List<String> changedFields = changedFields(before, after);
		List<String> redactedChanges = changedFields.stream().filter(REDACTED_FIELDS::contains).toList();
		var context = new LinkedHashMap<String, Object>();
		context.put("redactedFields", redactedChanges);
		auditWriter.recordWithChangedFields(action, AuditSource.PARTNER_MANAGEMENT,
				"BUSINESS_PARTNER", partner.getId(), changedFields,
				before == null ? Map.of() : safeData(before), safeData(after), context);
	}

	private List<String> changedFields(Snapshot before, Snapshot after) {
		var changed = new ArrayList<String>();
		if (before == null || !Objects.equals(before.name(), after.name())) changed.add("name");
		if (before == null || before.partnerType() != after.partnerType()) changed.add("partnerType");
		if (before == null || !Objects.equals(before.ownerName(), after.ownerName())) changed.add("ownerName");
		if (before == null || !Objects.equals(before.phone(), after.phone())) changed.add("phone");
		if (before == null || !Objects.equals(before.address(), after.address())) changed.add("address");
		if (before == null || !Objects.equals(before.memo(), after.memo())) changed.add("memo");
		if (before == null || before.active() != after.active()) changed.add("active");
		return List.copyOf(changed);
	}

	private Map<String, Object> safeData(Snapshot snapshot) {
		var data = new LinkedHashMap<String, Object>();
		data.put("name", snapshot.name());
		data.put("partnerType", snapshot.partnerType());
		data.put("active", snapshot.active());
		return data;
	}

	public record Snapshot(
			String name,
			PartnerType partnerType,
			String ownerName,
			String phone,
			String address,
			String memo,
			boolean active) {
	}
}
