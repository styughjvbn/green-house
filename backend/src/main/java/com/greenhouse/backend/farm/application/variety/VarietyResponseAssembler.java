package com.greenhouse.backend.farm.application.variety;

import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.dto.variety.VarietyConnectedOrchidGroupResponse;
import com.greenhouse.backend.farm.dto.variety.VarietyResponse;
import com.greenhouse.backend.farm.repository.inbound.InboundRecordRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupSummaryRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupVarietyReference;
import com.greenhouse.backend.farm.repository.orchid.VarietyInventorySummary;
import com.greenhouse.backend.work.application.operation.WorkOperationMetricsReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VarietyResponseAssembler {

  private final OrchidGroupRepository orchidGroupRepository;

  private final InboundRecordRepository inboundRecordRepository;

  private final OrchidGroupSummaryRepository summaries;

  private static final int WORK_BATCH_SIZE = 500;

  private final WorkOperationMetricsReader workOperationMetricsReader;

  public Page<VarietyResponse> assemble(Page<Variety> varieties) {
    var varietyIds = varieties.getContent().stream().map(Variety::getId).toList();
    if (varietyIds.isEmpty()) {
      return varieties.map(variety -> VarietyResponse.from(variety, 0, 0, 0, null, null));
    }
    var responses = summarize(varieties.getContent());
    return varieties.map(variety -> responses.get(variety.getId()));
  }

  public VarietyResponse assemble(Variety variety) {
    return summarize(List.of(variety)).get(variety.getId());
  }

  private Map<Long, VarietyResponse> summarize(List<Variety> varieties) {
    var ids = varieties.stream().map(Variety::getId).toList();
    var inventory =
        summaries.summarizeVarieties(ids).stream()
            .collect(Collectors.toMap(VarietyInventorySummary::varietyId, row -> row));
    var workDates = latestWorkDatesByVariety(ids);
    var inboundDates =
        inboundRecordRepository.findLatestInboundDatesByVarietyIds(ids).stream()
            .collect(Collectors.toMap(row -> (Long) row[0], row -> (LocalDate) row[1]));
    var result = new HashMap<Long, VarietyResponse>();
    for (Variety variety : varieties) {
      var quantities =
          inventory.getOrDefault(
              variety.getId(), new VarietyInventorySummary(variety.getId(), 0, 0, 0));
      result.put(
          variety.getId(),
          VarietyResponse.from(
              variety,
              quantities.groupCount(),
              quantities.quantity(),
              quantities.saleableQuantity(),
              inboundDates.get(variety.getId()),
              workDates.get(variety.getId())));
    }
    return result;
  }

  private Map<Long, LocalDate> latestWorkDatesByVariety(List<Long> varietyIds) {
    var result = new HashMap<Long, LocalDate>();
    try (var rows = summaries.streamVarietyReferences(varietyIds)) {
      var iterator = rows.iterator();
      while (iterator.hasNext()) {
        var batch = new ArrayList<OrchidGroupVarietyReference>(WORK_BATCH_SIZE);
        while (iterator.hasNext() && batch.size() < WORK_BATCH_SIZE) batch.add(iterator.next());
        var dates =
            workOperationMetricsReader.getLatestWorkDates(
                batch.stream().map(OrchidGroupVarietyReference::orchidGroupId).toList());
        for (var row : batch) {
          var date = dates.get(row.orchidGroupId());
          if (date != null)
            result.merge(
                row.varietyId(), date, (left, right) -> left.isAfter(right) ? left : right);
        }
      }
    }
    return result;
  }

  public List<VarietyConnectedOrchidGroupResponse> connectedOrchidGroups(Variety variety) {
    var orchidGroups = orchidGroupRepository.findByVarietyIdOrderByLocation(variety.getId());
    var latestWorkDates = latestWorkDates(orchidGroups);
    return orchidGroups.stream()
        .map(
            group ->
                new VarietyConnectedOrchidGroupResponse(
                    group.getId(),
                    formatLocation(group),
                    group.getQuantity(),
                    group.getStatus(),
                    latestWorkDates.get(group.getId())))
        .toList();
  }

  private Map<Long, LocalDate> latestWorkDates(List<OrchidGroup> orchidGroups) {
    return workOperationMetricsReader.getLatestWorkDates(
        orchidGroups.stream().map(OrchidGroup::getId).toList());
  }

  private String formatLocation(OrchidGroup orchidGroup) {
    var bedZone = orchidGroup.getBedZone();
    var physicalBed = bedZone.getPhysicalBed();
    var house = physicalBed.getHouse();
    return "%d동-%d다이 %s".formatted(house.getNumber(), physicalBed.getNumber(), bedZone.getName());
  }
}
