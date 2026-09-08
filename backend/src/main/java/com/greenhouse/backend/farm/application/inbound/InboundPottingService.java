package com.greenhouse.backend.farm.application.inbound;

import com.greenhouse.backend.common.application.RequestActorProvider;
import com.greenhouse.backend.farm.application.orchid.mutation.CreateInboundOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.CreateOrchidGroupMutationItem;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordPottingRequest;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordResponse;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.work.application.effect.WorkMutationLink;
import java.util.List;
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

	private final RequestActorProvider requestActorProvider;

	private final OrchidGroupMutationEngine mutationEngine;

	public InboundPottingResult potting(Long inboundRecordId, InboundRecordPottingRequest request, Long workOperationId,
			String effectKey) {
		var inboundRecord = inboundRecordFinder.find(inboundRecordId);
		inboundRecord.requirePottingAllowed();

		List<OrchidGroup> createdGroups;
		var mutationCommand = new CreateInboundOrchidGroupsMutationCommand(
				OrchidGroupMutationSources.work(workOperationId, effectKey), inboundRecord.getId(),
				request.results()
					.stream()
					.map(row -> new CreateOrchidGroupMutationItem(row.bedZoneId(),
							new OrchidGroupMutationDetails(inboundRecord.getVariety().getId(), row.quantity(),
									firstNonBlank(row.potSize(), inboundRecord.getPotSize()), row.ageYear(),
									DEFAULT_ORCHID_STATUS, row.placementType(), row.trayCount(),
									row.splitPlacementAllowed(), row.startPosition(), row.endPosition(), row.memo())))
					.toList(),
				request.pottingDate(), request.memo());
		var mutation = mutationEngine.createFromInbound(mutationCommand);
		List<Long> groupIds = mutation.entries().stream().map(entry -> entry.orchidGroupId()).toList();
		var groupsById = orchidGroupRepository.findAllById(groupIds)
			.stream()
			.collect(java.util.stream.Collectors.toMap(OrchidGroup::getId, group -> group));
		createdGroups = groupIds.stream().map(groupsById::get).toList();
		var mutationLink = new WorkMutationLink(mutation.mutationId(), mutation.correlationId());

		OrchidGroup representative = createdGroups.getFirst();
		int actualQuantity = createdGroups.stream().mapToInt(OrchidGroup::getQuantity).sum();
		inboundRecord.updateMetadata(inboundRecord.getInboundDate(), inboundRecord.getBottleCount(),
				inboundRecord.getEstimatedQuantity(), actualQuantity, inboundRecord.getTempLocation(),
				inboundRecord.getPottingDueDate(), representative.getPotSize(), representative.getAgeYear(),
				normalize(request.growthStage()), representative.getPlacementType(), representative.getTrayCount(),
				requestActorProvider.resolve(request.worker()), appendMemo(inboundRecord.getMemo(), request.memo()));
		inboundRecord.place(representative.getBedZone(), representative, request.pottingDate(), actualQuantity);
		return new InboundPottingResult(InboundRecordResponse.from(inboundRecordFinder.find(inboundRecord.getId())),
				createdGroups.stream().map(OrchidGroup::getId).toList(), actualQuantity, mutationLink);
	}

	private String firstNonBlank(String first, String second) {
		String normalizedFirst = normalize(first);
		return normalizedFirst != null ? normalizedFirst : normalize(second);
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
