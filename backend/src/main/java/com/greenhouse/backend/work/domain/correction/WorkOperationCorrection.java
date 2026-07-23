package com.greenhouse.backend.work.domain.correction;

import com.greenhouse.backend.work.domain.operation.WorkOperation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
		name = "work_operation_corrections",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_work_operation_correction_operation",
				columnNames = "correction_work_operation_id"))
public class WorkOperationCorrection {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "original_work_operation_id", nullable = false)
	private WorkOperation originalWorkOperation;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "correction_work_operation_id", nullable = false)
	private WorkOperation correctionWorkOperation;

	@Column(nullable = false, columnDefinition = "text")
	private String reason;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public WorkOperationCorrection(
			WorkOperation originalWorkOperation,
			WorkOperation correctionWorkOperation,
			String reason) {
		if (originalWorkOperation == correctionWorkOperation
				|| originalWorkOperation.getId() != null
				&& originalWorkOperation.getId().equals(correctionWorkOperation.getId())) {
			throw new IllegalArgumentException("원본 작업과 보정 작업은 달라야 합니다.");
		}
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("보정 사유가 필요합니다.");
		}
		this.originalWorkOperation = originalWorkOperation;
		this.correctionWorkOperation = correctionWorkOperation;
		this.reason = reason.trim();
	}
}
