package com.greenhouse.backend.work.application.correction;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.application.operation.WorkCommandReceipts;
import com.greenhouse.backend.work.application.operation.WorkOperationQueryService;
import com.greenhouse.backend.work.application.operation.WorkOperationSupport;
import com.greenhouse.backend.work.application.operation.WorkRequestFingerprint;
import com.greenhouse.backend.work.domain.correction.WorkOperationCorrection;
import com.greenhouse.backend.work.dto.correction.WorkOperationCorrectionsResponse;
import com.greenhouse.backend.work.dto.operation.WorkCorrectionDetailResponse;
import com.greenhouse.backend.work.repository.WorkCorrectionReceiptRepository;
import com.greenhouse.backend.work.repository.WorkOperationCorrectionRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class WorkOperationCorrectionService {

	private final WorkOperationRepository operationRepository;

	private final WorkOperationCorrectionRepository correctionRepository;

	private final WorkCorrectionReceiptRepository receiptRepository;

	private final WorkRequestFingerprint fingerprints;

	private final WorkCorrectionPort correctionPort;

	private final WorkOperationQueryService queryService;

	private final WorkOperationSupport support;

	private final WorkCorrectionQuantityService quantities;

	public WorkOperationCorrectionsResponse create(Long originalId, WorkCorrectionCommand request) {
		String key = WorkCommandReceipts.normalizeKey(request.idempotencyKey());
		String fingerprint = fingerprints.calculate(new Request(originalId, request));
		int inserted = receiptRepository.claim(key, fingerprint, support.now());
		var receipt = receiptRepository.findForUpdate(key);
		receipt.validate(fingerprint);
		if (receipt.getCorrectionId() != null)
			return response(originalId);
		if (inserted != 1)
			throw new IllegalStateException("완료되지 않은 보정 요청 기록입니다.");
		var original = operationRepository.findForUpdateById(originalId)
			.orElseThrow(() -> new NotFoundException("원본 작업을 찾을 수 없습니다."));
		if (request.workDate().isAfter(support.today()))
			throw new IllegalArgumentException("보정 작업일은 오늘 이후로 입력할 수 없습니다.");
		var correction = new WorkOperationCorrection(original, request.reason(), support.actor(request.worker()),
				normalize(request.memo()), support.now());
		var result = correctionPort.correct(originalId, () -> correctionRepository.save(correction).getId(), request);
		var link = result.mutationLink();
		correction.complete(result.resultDetails(), link == null ? null : link.mutationId(),
				link == null ? null : link.correlationId());
		receipt.complete(correction.getId());
		return response(originalId);
	}

	@Transactional(readOnly = true)
	public WorkOperationCorrectionsResponse get(Long originalId) {
		return response(originalId);
	}

	private WorkOperationCorrectionsResponse response(Long originalId) {
		var original = queryService.get(originalId);
		return new WorkOperationCorrectionsResponse(original,
				correctionRepository.findByOriginalWorkOperationIdOrderByCreatedAtAscIdAsc(originalId)
					.stream()
					.map(WorkCorrectionDetailResponse::from)
					.toList(),
				quantities.context(originalId), quantities.isEnabled());
	}

	private record Request(Long originalId, WorkCorrectionCommand command) {
	}

	private String normalize(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
