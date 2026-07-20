package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.dto.OrchidGroupMoveRequest;
import com.greenhouse.backend.farm.dto.OrchidGroupResponse;
import com.greenhouse.backend.work.application.OperationLevelWorkService;
import com.greenhouse.backend.work.domain.WorkType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class OrchidGroupMovementService {

	private final OperationLevelWorkService operationLevelWorkService;
	private final OrchidGroupReader orchidGroupReader;

	public OrchidGroupResponse move(Long orchidGroupId, OrchidGroupMoveRequest request) {
		var orchidGroup = orchidGroupReader.findDetailById(orchidGroupId)
				.orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
		if (isSamePlacement(orchidGroup, request)) {
			return OrchidGroupResponse.from(orchidGroup);
		}

		Map<String, Object> details = new LinkedHashMap<>();
		details.put("toBedZoneId", request.toBedZoneId());
		putIfNotNull(details, "startPosition", request.startPosition());
		putIfNotNull(details, "endPosition", request.endPosition());
		putIfNotNull(details, "worker", request.worker());
		putIfNotNull(details, "memo", request.memo());

		operationLevelWorkService.executeForTarget(
				"DIRECT_MOVE:" + UUID.randomUUID(),
				WorkType.MOVEMENT_CODE,
				"위치 이동",
				LocalDate.now(),
				request.worker(),
				request.memo(),
				orchidGroupId,
				details,
				request);
		return orchidGroupReader.findDetailById(orchidGroupId)
				.map(OrchidGroupResponse::from)
				.orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
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
