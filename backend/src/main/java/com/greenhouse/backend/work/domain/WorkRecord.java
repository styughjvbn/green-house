package com.greenhouse.backend.work.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "work_records")
public class WorkRecord extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "work_type", nullable = false)
	private String workType;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "work_type_id")
	private WorkType workTypeRef;

	@Column(name = "work_date", nullable = false)
	private LocalDate workDate;

	@Column(name = "target_type", nullable = false)
	private String targetType;

	@Column(name = "target_id")
	private Long targetId;

	@Column(name = "material_name")
	private String materialName;

	@Column(name = "dilution_ratio")
	private String dilutionRatio;

	private String quantity;

	private String worker;

	@Column(name = "from_bed_zone_id")
	private Long fromBedZoneId;

	@Column(name = "to_bed_zone_id")
	private Long toBedZoneId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private Map<String, Object> details;

	@Column(columnDefinition = "text")
	private String memo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private WorkRecordStatus status = WorkRecordStatus.ACTIVE;

	@Column(name = "canceled_at")
	private LocalDateTime canceledAt;

	@Column(name = "cancel_reason", columnDefinition = "text")
	private String cancelReason;

	public WorkRecord(
			WorkType workTypeRef,
			LocalDate workDate,
			String targetType,
			Long targetId,
			String materialName,
			String dilutionRatio,
			String quantity,
			String worker,
			String memo) {
		this(workTypeRef, workDate, targetType, targetId, materialName, dilutionRatio, quantity, worker, memo, null);
	}

	public WorkRecord(
			WorkType workTypeRef,
			LocalDate workDate,
			String targetType,
			Long targetId,
			String materialName,
			String dilutionRatio,
			String quantity,
			String worker,
			String memo,
			Map<String, Object> details) {
		this.workTypeRef = workTypeRef;
		this.workType = workTypeRef.getName();
		this.workDate = workDate;
		this.targetType = targetType;
		this.targetId = targetId;
		this.materialName = materialName;
		this.dilutionRatio = dilutionRatio;
		this.quantity = quantity;
		this.worker = worker;
		this.memo = memo;
		this.details = details;
		this.status = WorkRecordStatus.ACTIVE;
	}

	public void cancel(String cancelReason) {
		if (status == WorkRecordStatus.CANCELED) {
			return;
		}
		this.status = WorkRecordStatus.CANCELED;
		this.canceledAt = LocalDateTime.now();
		this.cancelReason = cancelReason;
	}

}
