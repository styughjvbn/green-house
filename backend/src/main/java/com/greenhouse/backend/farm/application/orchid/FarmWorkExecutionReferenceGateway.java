package com.greenhouse.backend.farm.application.orchid;

import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.structure.BedZoneRepository;
import com.greenhouse.backend.work.application.target.WorkExecutionReferenceGateway;
import com.greenhouse.backend.work.application.operation.WorkExecutionLocation;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FarmWorkExecutionReferenceGateway implements WorkExecutionReferenceGateway {

	private final OrchidGroupRepository orchidGroupRepository;
	private final BedZoneRepository bedZoneRepository;

	@Override
	public Map<Long, String> varietyNames(Collection<Long> orchidGroupIds) {
		if (orchidGroupIds.isEmpty()) {
			return Map.of();
		}
		return orchidGroupRepository.findNameRowsByIdIn(orchidGroupIds).stream()
				.collect(Collectors.toMap(
						row -> row.id(),
						row -> row.varietyName(),
						(left, right) -> left,
						LinkedHashMap::new));
	}

	@Override
	public Map<Long, WorkExecutionLocation> locations(Collection<Long> bedZoneIds) {
		if (bedZoneIds.isEmpty()) {
			return Map.of();
		}
		return bedZoneRepository.findLocationRowsByIdIn(bedZoneIds).stream()
				.collect(Collectors.toMap(
						row -> row.id(),
						row -> new WorkExecutionLocation(
								row.houseNumber(), row.physicalBedNumber(), row.bedZoneName()),
						(left, right) -> left,
						LinkedHashMap::new));
	}
}
