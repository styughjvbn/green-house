package com.greenhouse.backend.farm.application.variety;

import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.dto.variety.VarietyConnectedOrchidGroupResponse;
import com.greenhouse.backend.farm.dto.variety.VarietyResponse;
import com.greenhouse.backend.farm.repository.inbound.InboundRecordRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.work.application.operation.WorkOperationMetricsReader;
import java.time.LocalDate;
import java.util.Comparator;
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
	private final WorkOperationMetricsReader workOperationMetricsReader;

	public Page<VarietyResponse> assemble(Page<Variety> varieties) {
		var varietyIds = varieties.getContent().stream().map(Variety::getId).toList();
		if (varietyIds.isEmpty()) {
			return varieties.map(variety -> assemble(variety, List.of(), Map.of(), null));
		}
		var orchidGroups = orchidGroupRepository.findByVarietyIdInOrderByLocation(varietyIds);
		var groupsByVarietyId = orchidGroups.stream()
				.collect(Collectors.groupingBy(group -> group.getVariety().getId()));
		var latestWorkDates = latestWorkDates(orchidGroups);
		var latestInboundDates = inboundRecordRepository.findLatestInboundDatesByVarietyIds(varietyIds).stream()
				.collect(Collectors.toMap(
						row -> (Long) row[0],
						row -> (LocalDate) row[1]));
		return varieties.map(variety -> assemble(
				variety,
				groupsByVarietyId.getOrDefault(variety.getId(), List.of()),
				latestWorkDates,
				latestInboundDates.get(variety.getId())));
	}

	public VarietyResponse assemble(Variety variety) {
		var orchidGroups = orchidGroupRepository.findByVarietyIdOrderByLocation(variety.getId());
		return assemble(
				variety,
				orchidGroups,
				latestWorkDates(orchidGroups),
				inboundRecordRepository.findLatestInboundDateByVarietyId(variety.getId()));
	}

	public List<VarietyConnectedOrchidGroupResponse> connectedOrchidGroups(Variety variety) {
		var orchidGroups = orchidGroupRepository.findByVarietyIdOrderByLocation(variety.getId());
		var latestWorkDates = latestWorkDates(orchidGroups);
		return orchidGroups.stream()
				.map(group -> new VarietyConnectedOrchidGroupResponse(
						group.getId(),
						formatLocation(group),
						group.getQuantity(),
						group.getStatus(),
						latestWorkDates.get(group.getId())))
				.toList();
	}

	private VarietyResponse assemble(
			Variety variety,
			List<OrchidGroup> orchidGroups,
			Map<Long, LocalDate> latestWorkDates,
			LocalDate latestInboundDate) {
		long totalQuantity = orchidGroups.stream().mapToLong(OrchidGroup::getQuantity).sum();
		long saleableQuantity = orchidGroups.stream()
				.filter(group -> !List.of("주의", "이상", "병해충").contains(group.getStatus()))
				.mapToLong(OrchidGroup::getQuantity)
				.sum();
		LocalDate recentWorkDate = latestWorkDates.values().stream()
				.filter(java.util.Objects::nonNull)
				.max(Comparator.naturalOrder())
				.orElse(null);
		return VarietyResponse.from(
				variety,
				orchidGroups.size(),
				totalQuantity,
				saleableQuantity,
				latestInboundDate,
				recentWorkDate);
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
