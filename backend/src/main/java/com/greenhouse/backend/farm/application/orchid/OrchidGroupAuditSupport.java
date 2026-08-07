package com.greenhouse.backend.farm.application.orchid;

import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.application.AuditEvent;
import com.greenhouse.backend.audit.application.AuditRecorder;
import com.greenhouse.backend.audit.application.AuditRequestContext;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OrchidGroupAuditSupport {
	private static final Set<String> INACTIVE_STATUSES = Set.of("종료", "폐기", "판매 완료", "생성 취소");
	private static final List<String> FIELDS = List.of("varietyId", "ageYear", "potSize", "quantity",
			"houseId", "physicalBedId", "zoneId", "startPosition", "endPosition", "status");
	private final AuditRecorder auditRecorder;
	private final AuditRequestContext requestContext;

	public OrchidGroupAuditSupport(AuditRecorder auditRecorder, AuditRequestContext requestContext) {
		this.auditRecorder = auditRecorder;
		this.requestContext = requestContext;
	}

	public OrchidGroupAuditSnapshot snapshot(OrchidGroup group) {
		var zone = group.getBedZone();
		var bed = zone.getPhysicalBed();
		return new OrchidGroupAuditSnapshot(
				group.getVariety() == null ? null : group.getVariety().getId(), group.getAgeYear(), group.getPotSize(),
				group.getQuantity(), bed.getHouse().getId(), bed.getId(), zone.getId(), group.getStartPosition(),
				group.getEndPosition(), group.getStatus());
	}

	public List<String> detectChanges(OrchidGroupAuditSnapshot before, OrchidGroupAuditSnapshot after) {
		var changes = new ArrayList<String>();
		Object[] left = values(before);
		Object[] right = values(after);
		for (int index = 0; index < FIELDS.size(); index++) {
			if (!Objects.equals(left[index], right[index])) changes.add(FIELDS.get(index));
		}
		return List.copyOf(changes);
	}

	public AuditAction actionForCorrection(
			OrchidGroupAuditSnapshot before,
			OrchidGroupAuditSnapshot after) {
		if (before != null && after != null
				&& !INACTIVE_STATUSES.contains(before.status())
				&& INACTIVE_STATUSES.contains(after.status())) {
			return AuditAction.DEACTIVATED;
		}
		return AuditAction.UPDATED;
	}

	public Long record(Long entityId, AuditAction action, AuditSource source,
			OrchidGroupAuditSnapshot before, OrchidGroupAuditSnapshot after, Map<String, Object> contextData) {
		List<String> changedFields = detectChanges(before, after);
		if (changedFields.isEmpty()) return null;
		var location = after != null ? after : before;
		var identity = requestContext.current();
		return auditRecorder.record(new AuditEvent(identity.actorId(), identity.sessionId(), identity.clientInstanceId(),
				identity.requestId(), action, source, "ORCHID_GROUP", entityId, location.houseId(),
				location.physicalBedId(), location.zoneId(), location.varietyId(), changedFields, before, after,
				contextData == null ? Map.of() : contextData));
	}

	private Object[] values(OrchidGroupAuditSnapshot value) {
		if (value == null) return new Object[FIELDS.size()];
		return new Object[]{value.varietyId(), value.ageYear(), value.potSize(), value.quantity(), value.houseId(),
				value.physicalBedId(), value.zoneId(), value.startPosition(), value.endPosition(), value.status()};
	}
}
