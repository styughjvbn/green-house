package com.greenhouse.backend.farm.application.orchid;

import com.greenhouse.backend.audit.application.AuditEvent;
import com.greenhouse.backend.audit.application.AuditEventWriter;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroupStatusPolicy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrchidGroupAuditSupport {

  private final AuditEventWriter auditWriter;

  public OrchidGroupAuditSnapshot snapshot(OrchidGroup group) {
    var zone = group.getBedZone();
    var bed = zone.getPhysicalBed();
    return new OrchidGroupAuditSnapshot(
        group.getVariety() == null ? null : group.getVariety().getId(),
        group.getAgeYear(),
        group.getPotSize(),
        group.getQuantity(),
        bed.getHouse().getId(),
        bed.getId(),
        zone.getId(),
        group.getStartPosition(),
        group.getEndPosition(),
        group.getStatus(),
        group.getPlacementType(),
        group.getTrayCount(),
        group.getSplitPlacementAllowed(),
        group.getMemo());
  }

  public List<String> detectChanges(
      OrchidGroupAuditSnapshot before, OrchidGroupAuditSnapshot after) {
    return auditWriter.detectChanges(values(before), values(after));
  }

  public AuditAction actionForCorrection(
      OrchidGroupAuditSnapshot before, OrchidGroupAuditSnapshot after) {
    if (before != null
        && after != null
        && !OrchidGroupStatusPolicy.isInactive(before.status())
        && OrchidGroupStatusPolicy.isInactive(after.status())) {
      return AuditAction.DEACTIVATED;
    }
    return AuditAction.UPDATED;
  }

  public Long record(
      Long entityId,
      AuditAction action,
      AuditSource source,
      OrchidGroupAuditSnapshot before,
      OrchidGroupAuditSnapshot after,
      Map<String, Object> contextData) {
    List<String> changedFields = detectChanges(before, after);
    if (changedFields.isEmpty()) return null;
    var location = after != null ? after : before;
    var context = new LinkedHashMap<String, Object>();
    if (contextData != null) context.putAll(contextData);
    context.put("redactedFields", changedFields.contains("memo") ? List.of("memo") : List.of());
    return auditWriter.recordChanges(
        action,
        source,
        new AuditEvent.Target(
            "ORCHID_GROUP",
            entityId,
            location.houseId(),
            location.physicalBedId(),
            location.zoneId(),
            location.varietyId()),
        changedFields,
        safeData(before),
        safeData(after),
        context);
  }

  private Map<String, Object> values(OrchidGroupAuditSnapshot value) {
    if (value == null) return null;
    var data = new LinkedHashMap<String, Object>();
    data.put("varietyId", value.varietyId());
    data.put("ageYear", value.ageYear());
    data.put("potSize", value.potSize());
    data.put("quantity", value.quantity());
    data.put("houseId", value.houseId());
    data.put("physicalBedId", value.physicalBedId());
    data.put("zoneId", value.zoneId());
    data.put("startPosition", value.startPosition());
    data.put("endPosition", value.endPosition());
    data.put("status", value.status());
    data.put("placementType", value.placementType());
    data.put("trayCount", value.trayCount());
    data.put("splitPlacementAllowed", value.splitPlacementAllowed());
    data.put("memo", value.memo());
    return data;
  }

  private Map<String, Object> safeData(OrchidGroupAuditSnapshot value) {
    var data = values(value);
    if (data != null) data.remove("memo");
    return data;
  }
}
