package com.greenhouse.backend.farm.application.inbound;

import com.greenhouse.backend.farm.domain.inbound.InboundRecord;
import com.greenhouse.backend.farm.domain.inbound.InboundStatus;
import com.greenhouse.backend.farm.domain.inbound.InboundType;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryKind;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordResponse;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationEntryRepository;
import com.greenhouse.backend.work.api.operation.InboundWorkOperationLifecycleApi;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InboundRecordResponseAssembler {

  private final OrchidGroupRepository orchidGroupRepository;

  private final OrchidGroupMutationEntryRepository mutationEntryRepository;

  private final InboundWorkOperationLifecycleApi lifecycle;

  public InboundRecordResponse assemble(InboundRecord record) {
    var dates = pottingDatesByInboundRecordId(List.of(record));
    return InboundRecordResponse.from(
        record,
        orchidGroupRepository.findInboundResultDetailsByInboundRecordIdIn(List.of(record.getId())),
        dates.get(record.getId()),
        inboundIdsWithUndoablePotting(List.of(record)).contains(record.getId()));
  }

  public Set<Long> inboundIdsWithUndoablePotting(List<InboundRecord> records) {
    return lifecycle.findInboundIdsWithUndoablePotting(
        records.stream()
            .filter(
                record ->
                    record.getInboundType() == InboundType.FLASK_SEEDLING
                        && record.getStatus() == InboundStatus.PLACED)
            .map(InboundRecord::getId)
            .toList());
  }

  public Map<Long, List<OrchidGroup>> resultGroupsByInboundRecordId(List<InboundRecord> records) {
    if (records.isEmpty()) {
      return Map.of();
    }
    return orchidGroupRepository
        .findInboundResultDetailsByInboundRecordIdIn(
            records.stream().map(InboundRecord::getId).toList())
        .stream()
        .collect(Collectors.groupingBy(group -> group.getInboundRecord().getId()));
  }

  public Map<Long, LocalDate> pottingDatesByInboundRecordId(List<InboundRecord> records) {
    List<Long> flaskInboundIds =
        records.stream()
            .filter(record -> record.getInboundType() == InboundType.FLASK_SEEDLING)
            .map(InboundRecord::getId)
            .toList();
    if (flaskInboundIds.isEmpty()) {
      return Map.of();
    }
    return mutationEntryRepository
        .findInboundPottingDates(flaskInboundIds, OrchidGroupMutationEntryKind.CREATE)
        .stream()
        .collect(
            Collectors.toMap(
                OrchidGroupMutationEntryRepository.InboundPottingDateRow::getInboundRecordId,
                OrchidGroupMutationEntryRepository.InboundPottingDateRow::getPottingDate));
  }
}
