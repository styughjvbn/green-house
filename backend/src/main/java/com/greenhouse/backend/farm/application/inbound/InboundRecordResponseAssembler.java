package com.greenhouse.backend.farm.application.inbound;

import com.greenhouse.backend.farm.domain.inbound.InboundRecord;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordResponse;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InboundRecordResponseAssembler {

	private final OrchidGroupRepository orchidGroupRepository;

	public InboundRecordResponse assemble(InboundRecord record) {
		return InboundRecordResponse.from(record,
				orchidGroupRepository.findInboundResultDetailsByInboundRecordIdIn(List.of(record.getId())));
	}

	public Map<Long, List<OrchidGroup>> resultGroupsByInboundRecordId(List<InboundRecord> records) {
		if (records.isEmpty()) {
			return Map.of();
		}
		return orchidGroupRepository
			.findInboundResultDetailsByInboundRecordIdIn(records.stream().map(InboundRecord::getId).toList())
			.stream()
			.collect(Collectors.groupingBy(group -> group.getInboundRecord().getId()));
	}
}
