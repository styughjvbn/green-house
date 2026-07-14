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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
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
@Table(name = "work_operations")
public class WorkOperation extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "work_type_id", nullable = false)
	private WorkType workType;

	@Column(nullable = false, length = 150)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private WorkOperationStatus status;

	@Column(name = "planned_start_date", nullable = false)
	private LocalDate plannedStartDate;

	@Column(name = "planned_end_date")
	private LocalDate plannedEndDate;

	@Column(name = "actual_start_at")
	private LocalDateTime actualStartAt;

	@Column(name = "actual_end_at")
	private LocalDateTime actualEndAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_scope_type", nullable = false, length = 30)
	private WorkSourceScopeType sourceScopeType;

	@Column(name = "source_scope_id")
	private Long sourceScopeId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "source_condition_snapshot", columnDefinition = "jsonb")
	private Map<String, Object> sourceConditionSnapshot;

	@Column(name = "target_snapshot_at", nullable = false)
	private LocalDateTime targetSnapshotAt;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private Map<String, Object> details;

	@Column(length = 100)
	private String worker;

	@Column(columnDefinition = "text")
	private String memo;

	@Version
	@Column(nullable = false)
	private long version;

	public WorkOperation(
			WorkType workType,
			String title,
			LocalDate plannedStartDate,
			LocalDate plannedEndDate,
			WorkSourceScopeType sourceScopeType,
			Long sourceScopeId,
			Map<String, Object> sourceConditionSnapshot,
			Map<String, Object> details,
			String worker,
			String memo) {
		this.workType = workType;
		this.title = title;
		this.status = WorkOperationStatus.PLANNED;
		this.plannedStartDate = plannedStartDate;
		this.plannedEndDate = plannedEndDate;
		this.sourceScopeType = sourceScopeType;
		this.sourceScopeId = sourceScopeId;
		this.sourceConditionSnapshot = sourceConditionSnapshot;
		this.targetSnapshotAt = LocalDateTime.now();
		this.details = details;
		this.worker = worker;
		this.memo = memo;
	}

	public void complete(LocalDateTime completedAt) {
		if (status == WorkOperationStatus.COMPLETED) {
			return;
		}
		if (status != WorkOperationStatus.PLANNED && status != WorkOperationStatus.IN_PROGRESS) {
			throw new IllegalArgumentException("완료할 수 없는 작업 상태입니다.");
		}
		if (actualStartAt == null) {
			actualStartAt = completedAt;
		}
		actualEndAt = completedAt;
		status = WorkOperationStatus.COMPLETED;
	}
}
