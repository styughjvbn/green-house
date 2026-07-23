package com.greenhouse.backend.farm.application.orchid;

import com.greenhouse.backend.common.application.OrchidGroupUsage;
import com.greenhouse.backend.common.application.OrchidGroupUsageInspector;
import com.greenhouse.backend.farm.repository.inbound.InboundRecordRepository;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class FarmOrchidGroupUsageInspector implements OrchidGroupUsageInspector {

	private final InboundRecordRepository inboundRecordRepository;

	public FarmOrchidGroupUsageInspector(InboundRecordRepository inboundRecordRepository) {
		this.inboundRecordRepository = inboundRecordRepository;
	}

	@Override
	public List<OrchidGroupUsage> inspect(Set<Long> orchidGroupIds, Long sourceWorkOperationId) {
		long count = inboundRecordRepository.countByCreatedOrchidGroupIdIn(orchidGroupIds);
		return count == 0
				? List.of()
				: List.of(new OrchidGroupUsage("INBOUND", "입고 기록에 연결된 난 묶음이 있습니다.", count));
	}
}
