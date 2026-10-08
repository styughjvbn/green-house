package com.greenhouse.backend.farm.inbound.application;

import com.greenhouse.backend.farm.api.orchid.CreateInboundOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.api.orchid.CreateOrchidGroupMutationItem;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.mutation.engine.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.orchid.domain.OrchidGroup;
import com.greenhouse.backend.farm.orchid.repository.OrchidGroupRepository;
import com.greenhouse.backend.work.api.effect.InboundPottingCommand;
import com.greenhouse.backend.work.api.effect.WorkMutationLink;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class InboundPottingService {

  private static final String DEFAULT_ORCHID_STATUS = "정상";

  private final InboundRecordFinder inboundRecordFinder;

  private final OrchidGroupRepository orchidGroupRepository;

  private final OrchidGroupMutationEngine mutationEngine;

  private final InboundRecordResponseAssembler responseAssembler;

  public InboundPottingResult potting(
      Long inboundRecordId, InboundPottingCommand request, Long workOperationId, String effectKey) {
    var inboundRecord = inboundRecordFinder.find(inboundRecordId);
    inboundRecord.requirePottingAllowed();

    List<OrchidGroup> createdGroups;
    var mutationCommand =
        new CreateInboundOrchidGroupsMutationCommand(
            OrchidGroupMutationSources.work(workOperationId, effectKey),
            inboundRecord.getId(),
            request.results().stream()
                .map(
                    row ->
                        new CreateOrchidGroupMutationItem(
                            row.bedZoneId(),
                            new OrchidGroupMutationDetails(
                                inboundRecord.getVariety().getId(),
                                row.quantity(),
                                normalize(row.potSize()),
                                row.ageYear(),
                                DEFAULT_ORCHID_STATUS,
                                row.placementType(),
                                row.trayCount(),
                                row.splitPlacementAllowed(),
                                row.startPosition(),
                                row.endPosition(),
                                row.memo())))
                .toList(),
            request.pottingDate(),
            request.memo());
    var mutation = mutationEngine.createFromInbound(mutationCommand);
    List<Long> groupIds = mutation.entries().stream().map(entry -> entry.orchidGroupId()).toList();
    var groupsById =
        orchidGroupRepository.findAllById(groupIds).stream()
            .collect(Collectors.toMap(OrchidGroup::getId, group -> group));
    createdGroups = groupIds.stream().map(groupsById::get).toList();
    var mutationLink = new WorkMutationLink(mutation.mutationId(), mutation.correlationId());

    int actualQuantity = createdGroups.stream().mapToInt(OrchidGroup::getQuantity).sum();
    inboundRecord.completePotting();
    return new InboundPottingResult(
        responseAssembler.assemble(inboundRecordFinder.find(inboundRecord.getId())),
        createdGroups.stream().map(OrchidGroup::getId).toList(),
        actualQuantity,
        mutationLink);
  }

  private String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
