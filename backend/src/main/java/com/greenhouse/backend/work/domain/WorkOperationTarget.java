package com.greenhouse.backend.work.domain;

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
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "work_operation_targets")
public class WorkOperationTarget {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "work_operation_id", nullable = false)
	private WorkOperation workOperation;

	@Column(name = "orchid_group_id")
	private Long orchidGroupId;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_reference_type", nullable = false, length = 30)
	private WorkTargetReferenceType targetReferenceType;

	@Column(name = "inbound_record_id")
	private Long inboundRecordId;

	@Enumerated(EnumType.STRING)
	@Column(name = "inclusion_source", nullable = false, length = 30)
	private WorkTargetInclusionSource inclusionSource;

	@Column(name = "source_reference_id")
	private Long sourceReferenceId;

	@Column(name = "included_at", nullable = false)
	private LocalDateTime includedAt;

	@Column(name = "excluded_at")
	private LocalDateTime excludedAt;

	@Column(name = "exclusion_reason", columnDefinition = "text")
	private String exclusionReason;

	@Column(name = "variety_id_snapshot")
	private Long varietyIdSnapshot;

	@Column(name = "variety_name_snapshot", nullable = false, length = 150)
	private String varietyNameSnapshot;

	@Column(name = "age_year_snapshot")
	private Integer ageYearSnapshot;

	@Column(name = "pot_size_code_snapshot", length = 50)
	private String potSizeCodeSnapshot;

	@Column(name = "pot_size_snapshot", length = 50)
	private String potSizeSnapshot;

	@Column(name = "quantity_snapshot", nullable = false)
	private Integer quantitySnapshot;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "location_snapshot", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> locationSnapshot;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public WorkOperationTarget(
			WorkOperation workOperation,
			Long orchidGroupId,
			WorkTargetInclusionSource inclusionSource,
			Long sourceReferenceId,
			Long varietyIdSnapshot,
			String varietyNameSnapshot,
			Integer ageYearSnapshot,
			String potSizeCodeSnapshot,
			String potSizeSnapshot,
			Integer quantitySnapshot,
			Map<String, Object> locationSnapshot) {
		this.workOperation = workOperation;
		this.orchidGroupId = orchidGroupId;
		this.targetReferenceType = WorkTargetReferenceType.ORCHID_GROUP;
		this.inclusionSource = inclusionSource;
		this.sourceReferenceId = sourceReferenceId;
		this.includedAt = LocalDateTime.now();
		this.varietyIdSnapshot = varietyIdSnapshot;
		this.varietyNameSnapshot = varietyNameSnapshot;
		this.ageYearSnapshot = ageYearSnapshot;
		this.potSizeCodeSnapshot = potSizeCodeSnapshot;
		this.potSizeSnapshot = potSizeSnapshot;
		this.quantitySnapshot = quantitySnapshot;
		this.locationSnapshot = locationSnapshot;
	}

	public static WorkOperationTarget inboundRecord(
			WorkOperation workOperation,
			Long inboundRecordId,
			Long varietyIdSnapshot,
			String varietyNameSnapshot,
			Integer quantitySnapshot,
			String potSizeSnapshot,
			Map<String, Object> locationSnapshot) {
		WorkOperationTarget target = new WorkOperationTarget();
		target.workOperation = workOperation;
		target.targetReferenceType = WorkTargetReferenceType.INBOUND_RECORD;
		target.inboundRecordId = inboundRecordId;
		target.inclusionSource = WorkTargetInclusionSource.INBOUND_RECORD;
		target.sourceReferenceId = inboundRecordId;
		target.includedAt = LocalDateTime.now();
		target.varietyIdSnapshot = varietyIdSnapshot;
		target.varietyNameSnapshot = varietyNameSnapshot;
		target.quantitySnapshot = quantitySnapshot;
		target.potSizeSnapshot = potSizeSnapshot;
		target.locationSnapshot = locationSnapshot;
		return target;
	}

	public void refreshInboundSnapshot(
			Long varietyIdSnapshot,
			String varietyNameSnapshot,
			Integer quantitySnapshot,
			String potSizeSnapshot,
			Map<String, Object> locationSnapshot) {
		if (targetReferenceType != WorkTargetReferenceType.INBOUND_RECORD) {
			throw new IllegalStateException("입고 기록 대상만 최신 입고 정보로 갱신할 수 있습니다.");
		}
		this.varietyIdSnapshot = varietyIdSnapshot;
		this.varietyNameSnapshot = varietyNameSnapshot;
		this.quantitySnapshot = quantitySnapshot;
		this.potSizeSnapshot = potSizeSnapshot;
		this.locationSnapshot = locationSnapshot;
	}
}
