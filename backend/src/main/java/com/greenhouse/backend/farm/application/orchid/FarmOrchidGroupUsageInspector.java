package com.greenhouse.backend.farm.application.orchid;

import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
@RequiredArgsConstructor
public class FarmOrchidGroupUsageInspector implements OrchidGroupUsageInspector {

	private final OrchidGroupRepository orchidGroupRepository;

	@Override
	public List<OrchidGroupUsage> inspect(Set<Long> orchidGroupIds, Long sourceWorkOperationId) {
		long count = orchidGroupRepository.countByIdInAndInboundRecordIsNotNull(orchidGroupIds);
		return count == 0 ? List.of() : List.of(new OrchidGroupUsage("INBOUND", "입고 기록에 연결된 난 묶음이 있습니다.", count));
	}

	@Override
	public List<OrchidGroupUsage> inspect(Set<Long> orchidGroupIds, Long sourceWorkOperationId,
			Set<Long> allowedInboundRecordIds) {
		long count = orchidGroupRepository.countInboundReferencesOutside(orchidGroupIds, allowedInboundRecordIds);
		return count == 0 ? List.of() : List.of(new OrchidGroupUsage("INBOUND", "다른 입고 기록에 연결된 난 묶음이 있습니다.", count));
	}

}
