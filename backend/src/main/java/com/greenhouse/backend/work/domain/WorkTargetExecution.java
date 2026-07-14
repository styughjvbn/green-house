package com.greenhouse.backend.work.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "work_target_executions")
public class WorkTargetExecution extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "work_operation_target_id", nullable = false, unique = true)
	private WorkOperationTarget target;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private WorkTargetExecutionStatus status;

	@Column(name = "started_at")
	private LocalDateTime startedAt;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@Column(length = 100)
	private String worker;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "result_details", columnDefinition = "jsonb")
	private Map<String, Object> resultDetails;

	@Column(name = "effect_applied_at")
	private LocalDateTime effectAppliedAt;

	@Version
	@Column(nullable = false)
	private long version;

	public WorkTargetExecution(WorkOperationTarget target) {
		this.target = target;
		this.status = WorkTargetExecutionStatus.PENDING;
	}

	public void complete(LocalDateTime completedAt, String worker) {
		if (status == WorkTargetExecutionStatus.COMPLETED) {
			return;
		}
		if (status != WorkTargetExecutionStatus.PENDING && status != WorkTargetExecutionStatus.IN_PROGRESS) {
			throw new IllegalArgumentException("완료할 수 없는 작업 대상 상태입니다.");
		}
		if (startedAt == null) {
			startedAt = completedAt;
		}
		this.completedAt = completedAt;
		this.worker = worker;
		this.status = WorkTargetExecutionStatus.COMPLETED;
	}
}
