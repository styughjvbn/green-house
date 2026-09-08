package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.work.repository.WorkCommandReceiptRepository;
import java.time.Clock;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class WorkCommandReceipts {

	private final WorkCommandReceiptRepository repository;

	private final WorkRequestFingerprint fingerprints;

	private final Clock clock;

	public List<Long> execute(String scope, String key, Object request, Supplier<List<Long>> action) {
		String identity = scope + ":" + normalizeKey(key);
		String fingerprint = fingerprints.calculate(request);
		int inserted = repository.claim(identity, fingerprint, TimeConfig.utcNow(clock));
		var receipt = repository.findForUpdate(identity);
		receipt.validate(fingerprint);
		if (receipt.getResultOperationIds() != null) {
			return receipt.getResultOperationIds();
		}
		if (inserted != 1) {
			throw new IllegalStateException("완료되지 않은 작업 요청 기록입니다.");
		}
		receipt.complete(action.get());
		return receipt.getResultOperationIds();
	}

	public static String normalizeKey(String key) {
		if (key == null || key.isBlank() || key.trim().length() > 100) {
			throw new IllegalArgumentException("멱등 키는 1~100자여야 합니다.");
		}
		return key.trim();
	}

}
