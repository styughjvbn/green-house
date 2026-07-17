package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.farm.domain.InboundRecord;
import com.greenhouse.backend.farm.domain.InboundStatus;
import com.greenhouse.backend.farm.domain.InboundType;
import com.greenhouse.backend.farm.repository.InboundRecordRepository;
import com.greenhouse.backend.work.application.InboundPottingPlanGateway;
import com.greenhouse.backend.work.application.InboundPottingPlanTarget;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FarmInboundPottingPlanGateway implements InboundPottingPlanGateway {

	private final InboundRecordRepository inboundRecordRepository;

	public FarmInboundPottingPlanGateway(InboundRecordRepository inboundRecordRepository) {
		this.inboundRecordRepository = inboundRecordRepository;
	}

	@Override
	public List<InboundPottingPlanTarget> findCandidates() {
		return inboundRecordRepository
				.findByInboundTypeAndStatusInAndCreatedOrchidGroupIsNullOrderByPottingDueDateAscIdAsc(
						InboundType.FLASK_SEEDLING,
						List.of(InboundStatus.TEMP_STORED, InboundStatus.POTTING_PENDING))
				.stream().map(this::toTarget).toList();
	}

	@Override
	public List<InboundPottingPlanTarget> resolve(List<Long> inboundRecordIds) {
		List<InboundRecord> records = findRecords(inboundRecordIds);
		if (records.size() != inboundRecordIds.size()) {
			throw new IllegalArgumentException("선택한 입고 기록 중 찾을 수 없는 항목이 있습니다.");
		}
		if (records.stream().anyMatch(record -> record.getInboundType() != InboundType.FLASK_SEEDLING
				|| record.getStatus() == InboundStatus.CANCELED
				|| record.getCreatedOrchidGroup() != null)) {
			throw new IllegalArgumentException("포트 작업 대기 중인 유리병 모종만 계획할 수 있습니다.");
		}
		return records.stream().map(this::toTarget).toList();
	}

	@Override
	public List<InboundPottingPlanTarget> findCurrent(List<Long> inboundRecordIds) {
		return findRecords(inboundRecordIds).stream().map(this::toTarget).toList();
	}

	private List<InboundRecord> findRecords(List<Long> inboundRecordIds) {
		return inboundRecordRepository.findByIdIn(inboundRecordIds);
	}

	private InboundPottingPlanTarget toTarget(InboundRecord record) {
		return new InboundPottingPlanTarget(
				record.getId(), record.getVariety().getId(), record.getVariety().getName(),
				record.getStatus().name(), record.getEstimatedQuantity(), record.getActualQuantity(),
				record.getTempLocation(), record.getPottingDueDate(), record.getPotSize());
	}
}
