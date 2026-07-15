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
import jakarta.persistence.UniqueConstraint;
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
@Table(
		name = "work_applied_effects",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_work_applied_effect_operation_key_kind",
				columnNames = {"work_operation_id", "effect_key", "effect_kind"}))
public class WorkAppliedEffect extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
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
}
