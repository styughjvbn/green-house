package com.greenhouse.backend.work.domain.effect;

import com.greenhouse.backend.common.domain.BaseEntity;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.target.WorkOperationTarget;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
		name = "work_applied_effects",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_work_applied_effect_operation_key_kind",
				columnNames = {"work_operation_id", "effect_key", "effect_kind"}))
public class WorkAppliedEffect extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "work_applied_effects_id_seq")
	@SequenceGenerator(name = "work_applied_effects_id_seq", sequenceName = "work_applied_effects_id_seq", allocationSize = 50)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "work_operation_id", nullable = false)
	private WorkOperation workOperation;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "work_operation_target_id")
	private WorkOperationTarget target;

	@Column(name = "effect_key", nullable = false, length = 100)
	private String effectKey;

	@Enumerated(EnumType.STRING)
	@Column(name = "effect_kind", nullable = false, length = 30)
	private WorkEffectKind effectKind;

	@Column(name = "handler_code", nullable = false, length = 50)
	private String handlerCode;

	@Column(name = "applied_at", nullable = false)
	private LocalDateTime appliedAt;

	@Column(name = "canceled_at")
	private LocalDateTime canceledAt;

	@Column(length = 100)
	private String worker;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "command_details", columnDefinition = "jsonb")
	private Map<String, Object> commandDetails;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "result_details", columnDefinition = "jsonb")
	private Map<String, Object> resultDetails;

	@Column(name = "mutation_id")
	private Long mutationId;

	@Column(name = "correlation_id")
	private UUID correlationId;

	public WorkAppliedEffect(
			WorkOperation workOperation,
			WorkOperationTarget target,
			String effectKey,
			WorkEffectKind effectKind,
			String handlerCode,
			LocalDateTime appliedAt,
			String worker,
			Map<String, Object> commandDetails,
			Map<String, Object> resultDetails) {
		this.workOperation = workOperation;
		this.target = target;
		this.effectKey = effectKey;
		this.effectKind = effectKind;
		this.handlerCode = handlerCode;
		this.appliedAt = appliedAt;
		this.worker = worker;
		this.commandDetails = commandDetails;
		this.resultDetails = resultDetails;
	}

	public void cancel(LocalDateTime canceledAt) {
		if (this.canceledAt == null) {
			this.canceledAt = canceledAt;
		}
	}

	public void linkMutation(Long mutationId, UUID correlationId) {
		if (mutationId == null || correlationId == null) {
			throw new IllegalArgumentException("작업 효과에 연결할 Mutation 정보가 필요합니다.");
		}
		if (this.mutationId != null && !this.mutationId.equals(mutationId)) {
			throw new IllegalStateException("작업 효과는 다른 Mutation으로 변경할 수 없습니다.");
		}
		this.mutationId = mutationId;
		this.correlationId = correlationId;
	}
}
