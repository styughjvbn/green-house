package com.greenhouse.backend.farm.application.inbound;

import com.greenhouse.backend.farm.domain.inbound.InboundRecord;
import com.greenhouse.backend.farm.domain.inbound.InboundStatus;
import com.greenhouse.backend.farm.domain.inbound.InboundType;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.structure.domain.BedZone;
import com.greenhouse.backend.work.api.operation.RecordInboundWorkCommand;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class InboundWorkOperationRequestFactory {

  public RecordInboundWorkCommand create(InboundRecord record, List<OrchidGroup> createdGroups) {
    OrchidGroup representative = createdGroups.isEmpty() ? null : createdGroups.getFirst();
    int quantity =
        createdGroups.isEmpty()
            ? record.getEstimatedQuantity()
            : createdGroups.stream().mapToInt(OrchidGroup::getQuantity).sum();
    return new RecordInboundWorkCommand(
        record.getId(),
        record.getInboundDate(),
        record.getVariety().getId(),
        record.getVariety().getName(),
        quantity,
        representative == null ? null : representative.getPotSize(),
        locationSnapshot(record, representative),
        createdGroups.stream().map(OrchidGroup::getId).toList(),
        record.getWorker(),
        workMemo(record),
        workDetails(record, createdGroups));
  }

  private Map<String, Object> workDetails(InboundRecord record, List<OrchidGroup> createdGroups) {
    Map<String, Object> details = new LinkedHashMap<>();
    putDetail(details, "inboundRecordId", record.getId());
    putDetail(details, "inboundType", record.getInboundType());
    putDetail(details, "status", record.getStatus());
    putDetail(details, "varietyId", record.getVariety().getId());
    putDetail(details, "genus", record.getVariety().getGenus());
    putDetail(details, "varietyName", record.getVariety().getName());
    putDetail(details, "estimatedQuantity", record.getEstimatedQuantity());
    putDetail(details, "tempLocation", record.getTempLocation());
    putDetail(details, "pottingDueDate", record.getPottingDueDate());
    if (!createdGroups.isEmpty()) {
      details.put("createdOrchidGroupIds", createdGroups.stream().map(OrchidGroup::getId).toList());
    }
    return details;
  }

  private Map<String, Object> locationSnapshot(InboundRecord record, OrchidGroup representative) {
    Map<String, Object> location = new LinkedHashMap<>();
    if (representative != null) {
      BedZone zone = representative.getBedZone();
      putDetail(location, "houseId", zone.getPhysicalBed().getHouse().getId());
      putDetail(location, "houseNumber", zone.getPhysicalBed().getHouse().getNumber());
      putDetail(location, "physicalBedId", zone.getPhysicalBed().getId());
      putDetail(location, "physicalBedNumber", zone.getPhysicalBed().getNumber());
      putDetail(location, "bedZoneId", zone.getId());
      putDetail(location, "bedZoneName", zone.getName());
    } else {
      putDetail(location, "tempLocation", record.getTempLocation());
      putDetail(location, "pottingDueDate", record.getPottingDueDate());
    }
    return location;
  }

  private String workMemo(InboundRecord record) {
    String autoMemo =
        String.join(
            "\n",
            "입고 유형: " + formatInboundType(record.getInboundType()),
            "품종: " + record.getVariety().getName(),
            "상태: " + formatInboundStatus(record.getStatus()));
    return appendMemo(record.getMemo(), autoMemo);
  }

  private String formatInboundType(InboundType inboundType) {
    return switch (inboundType) {
      case FLASK_SEEDLING -> "유리병 모종";
      case POTTED_SEEDLING -> "포트 모종";
      case PRODUCT_POT -> "상품분";
      case SAMPLE -> "샘플";
      case ETC -> "기타";
    };
  }

  private String formatInboundStatus(InboundStatus status) {
    return switch (status) {
      case POTTING_PENDING -> "포트작업대기";
      case POTTING_IN_PROGRESS -> "작업중";
      case PLACED -> "배치완료";
      case CANCELED -> "취소";
    };
  }

  private void putDetail(Map<String, Object> details, String key, Object value) {
    if (value != null) {
      details.put(key, value);
    }
  }

  private String appendMemo(String base, String extra) {
    String normalizedBase = normalize(base);
    String normalizedExtra = normalize(extra);
    if (normalizedExtra == null) {
      return normalizedBase;
    }
    if (normalizedBase == null) {
      return normalizedExtra;
    }
    return normalizedBase + "\n" + normalizedExtra;
  }

  private String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
