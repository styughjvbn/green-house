package com.greenhouse.backend.farm.application.orchid;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.orchid.PotSizeCode;
import com.greenhouse.backend.farm.dto.orchid.DerivedOrchidGroupResponse;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupResponse;
import com.greenhouse.backend.farm.repository.orchid.DerivedOrchidGroupMemberRow;
import com.greenhouse.backend.farm.repository.orchid.DerivedOrchidGroupSummaryRow;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupSummaryRepository;
import java.time.Clock;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DerivedOrchidGroupService {

  private static final String UNKNOWN_AGE = "UNSPECIFIED";

  private final Clock clock;

  private final OrchidGroupSummaryRepository summaries;

  public List<DerivedOrchidGroupResponse> getGroups(
      Long varietyId,
      Integer ageYear,
      PotSizeCode potSizeCode,
      Long houseId,
      String status,
      String keyword) {
    var businessDate = TimeConfig.farmToday(clock);
    if (potSizeCode == PotSizeCode.UNMAPPED) return List.of();
    Map<GroupKey, Summary> groups = new LinkedHashMap<>();
    try (var rows =
        summaries.streamDerivedSummaries(
            varietyId, potSizeCode, houseId, normalize(status), normalize(keyword))) {
      rows.forEach(
          row -> {
            Integer currentAge =
                OrchidGroupResponse.calculateAgeYear(
                    row.baseAgeYear(), row.inboundDate(), row.createdAt(), businessDate);
            if (ageYear != null && !ageYear.equals(currentAge)) return;
            var key = new GroupKey(row.varietyId(), currentAge, row.potSizeCode());
            groups.computeIfAbsent(key, ignored -> new Summary(row)).add(row);
          });
    }
    return groups.entrySet().stream()
        .map(entry -> entry.getValue().toResponse(entry.getKey()))
        .sorted(
            Comparator.comparing(DerivedOrchidGroupResponse::varietyName)
                .thenComparing(
                    DerivedOrchidGroupResponse::ageYear, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(response -> response.potSizeCode().name()))
        .toList();
  }

  public List<OrchidGroupResponse> getMembers(
      String groupKey, Long houseId, String status, String keyword) {
    var businessDate = TimeConfig.farmToday(clock);
    GroupKey key = parse(groupKey);
    List<OrchidGroupResponse> members;
    try (var rows =
        summaries.streamDerivedMembers(
            key.varietyId(), key.potSizeCode(), houseId, normalize(status), normalize(keyword))) {
      members =
          rows.filter(
                  row ->
                      Objects.equals(
                          key.ageYear(),
                          OrchidGroupResponse.calculateAgeYear(
                              row.baseAgeYear(), row.inboundDate(), row.createdAt(), businessDate)))
              .map(row -> toMember(row, key.ageYear()))
              .toList();
    }
    if (members.isEmpty()) {
      throw new NotFoundException("현재 조건에 해당하는 자동 그룹을 찾을 수 없습니다.");
    }
    return members;
  }

  private OrchidGroupResponse toMember(DerivedOrchidGroupMemberRow row, Integer ageYear) {
    return new OrchidGroupResponse(
        row.id(),
        row.bedZoneId(),
        row.varietyId(),
        row.varietyColor(),
        row.genus(),
        row.varietyName(),
        row.quantity(),
        row.potSize(),
        row.potSizeCode(),
        ageYear,
        row.status(),
        row.placementType(),
        row.trayCount(),
        row.splitPlacementAllowed(),
        row.startPosition(),
        row.endPosition(),
        row.sortOrder(),
        row.memo(),
        row.houseId(),
        row.houseNumber(),
        row.physicalBedNumber(),
        row.bedZoneName());
  }

  private static final class Summary {
    private final DerivedOrchidGroupSummaryRow first;
    private final Set<Long> zones = new HashSet<>();
    private int count;
    private int quantity;

    private Summary(DerivedOrchidGroupSummaryRow first) {
      this.first = first;
    }

    private void add(DerivedOrchidGroupSummaryRow row) {
      count++;
      quantity += row.quantity();
      zones.add(row.bedZoneId());
    }

    private DerivedOrchidGroupResponse toResponse(GroupKey key) {
      return new DerivedOrchidGroupResponse(
          key.serialize(),
          key.varietyId(),
          first.varietyName(),
          first.genus(),
          key.ageYear(),
          key.potSizeCode(),
          first.potSize(),
          count,
          quantity,
          zones.size());
    }
  }

  private GroupKey parse(String value) {
    String[] parts = value.split(":", -1);
    if (parts.length != 3) {
      throw new IllegalArgumentException("자동 그룹 키 형식이 올바르지 않습니다.");
    }
    try {
      Long varietyId = Long.valueOf(parts[0]);
      Integer ageYear = UNKNOWN_AGE.equals(parts[1]) ? null : Integer.valueOf(parts[1]);
      PotSizeCode potSizeCode = PotSizeCode.valueOf(parts[2]);
      if (potSizeCode == PotSizeCode.UNMAPPED) {
        throw new IllegalArgumentException("검수 중인 화분 크기는 자동 그룹으로 사용할 수 없습니다.");
      }
      return new GroupKey(varietyId, ageYear, potSizeCode);
    } catch (IllegalArgumentException exception) {
      if (exception.getMessage() != null && exception.getMessage().contains("검수 중인")) {
        throw exception;
      }
      throw new IllegalArgumentException("자동 그룹 키 형식이 올바르지 않습니다.");
    }
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  private record GroupKey(Long varietyId, Integer ageYear, PotSizeCode potSizeCode) {
    private String serialize() {
      return varietyId + ":" + (ageYear == null ? UNKNOWN_AGE : ageYear) + ":" + potSizeCode.name();
    }
  }
}
