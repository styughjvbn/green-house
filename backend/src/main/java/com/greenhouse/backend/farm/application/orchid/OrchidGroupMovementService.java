package com.greenhouse.backend.farm.application.orchid;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.common.application.RequestActorProvider;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupMoveRequest;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupResponse;
import com.greenhouse.backend.work.application.operation.ImmediateWorkExecutionService;
import com.greenhouse.backend.work.domain.operation.WorkType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;

@Service
@Transactional
@RequiredArgsConstructor
public class OrchidGroupMovementService {

	private final ImmediateWorkExecutionService immediateWorkExecutionService;
	private final OrchidGroupReader orchidGroupReader;
	private final Clock clock;
	private final RequestActorProvider requestActorProvider;
	private final OrchidGroupAuditSupport auditSupport;

	public OrchidGroupResponse move(Long orchidGroupId, OrchidGroupMoveRequest request) {
		var orchidGroup = orchidGroupReader.findDetailById(orchidGroupId)
				.orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
		if (isSamePlacement(orchidGroup, request)) {
			return OrchidGroupResponse.from(orchidGroup);
		}
		OrchidGroupAuditSnapshot before = auditSupport.snapshot(orchidGroup);

		Map<String, Object> details = new LinkedHashMap<>();
		details.put("toBedZoneId", request.toBedZoneId());
		putIfNotNull(details, "startPosition", request.startPosition());
		putIfNotNull(details, "endPosition", request.endPosition());
		String worker = requestActorProvider.resolve(request.worker());
		putIfNotNull(details, "worker", worker);
		putIfNotNull(details, "memo", request.memo());

		immediateWorkExecutionService.executeForTarget(
				"DIRECT_MOVE:" + UUID.randomUUID(),
				WorkType.MOVEMENT_CODE,
				"위치 이동",
				TimeConfig.farmToday(clock),
				worker,
				request.memo(),
				orchidGroupId,
				details,
				request);
		OrchidGroup moved = orchidGroupReader.findDetailById(orchidGroupId)
				.orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
		auditSupport.record(orchidGroupId, AuditAction.MOVED, AuditSource.WORK_RECORD,
				before, auditSupport.snapshot(moved), Map.of());
		return OrchidGroupResponse.from(moved);
	}

	private boolean isSamePlacement(
			OrchidGroup orchidGroup,
			OrchidGroupMoveRequest request) {
		return orchidGroup.getBedZone().getId().equals(request.toBedZoneId())
				&& isSameNumber(orchidGroup.getStartPosition(), request.startPosition())
				&& isSameNumber(orchidGroup.getEndPosition(), request.endPosition());
	}

	private boolean isSameNumber(BigDecimal left, BigDecimal right) {
		if (left == null || right == null) {
			return left == right;
		}
		return left.compareTo(right) == 0;
	}

	private void putIfNotNull(Map<String, Object> details, String key, Object value) {
		if (value != null) {
			details.put(key, value);
		}
	}
}
