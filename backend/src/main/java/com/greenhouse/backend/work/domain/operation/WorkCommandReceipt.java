package com.greenhouse.backend.work.domain.operation;

import com.greenhouse.backend.common.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "work_command_receipts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkCommandReceipt {

	@Id
	@Column(name = "receipt_key", length = 255)
	private String receiptKey;

	@Column(name = "request_fingerprint", length = 64)
	private String requestFingerprint;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "result_operation_ids", columnDefinition = "jsonb")
	private List<Long> resultOperationIds;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	public void validate(String fingerprint) {
		if (requestFingerprint == null) {
			throw new ConflictException("IDEMPOTENCY_REPLAY_UNAVAILABLE",
					"과거 요청 원문이 없어 재실행 내용을 확인할 수 없습니다. 기존 작업을 조회해 주세요.");
		}
		if (!requestFingerprint.equals(fingerprint)) {
			throw new ConflictException("IDEMPOTENCY_KEY_REUSED", "같은 멱등 키를 다른 작업 요청에 사용할 수 없습니다.");
		}
	}

	public void complete(List<Long> operationIds) {
		if (resultOperationIds != null || operationIds == null || operationIds.isEmpty()) {
			throw new IllegalStateException("요청 결과는 작업 식별자로 한 번만 확정해야 합니다.");
		}
		resultOperationIds = List.copyOf(operationIds);
	}

}
