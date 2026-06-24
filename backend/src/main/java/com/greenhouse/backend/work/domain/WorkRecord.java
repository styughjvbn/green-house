package com.greenhouse.backend.work.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "work_records")
public class WorkRecord extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "work_type", nullable = false)
	private String workType;

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

	@Column(columnDefinition = "text")
	private String memo;

	protected WorkRecord() {
	}

	public WorkRecord(String workType, LocalDate workDate, String targetType, Long targetId, String worker, String memo) {
		this.workType = workType;
		this.workDate = workDate;
		this.targetType = targetType;
		this.targetId = targetId;
		this.worker = worker;
		this.memo = memo;
	}

	public WorkRecord(
		String workType,
		LocalDate workDate,
		String targetType,
		Long targetId,
		String materialName,
		String dilutionRatio,
		String quantity,
		String worker,
		String memo
	) {
		this.workType = workType;
		this.workDate = workDate;
		this.targetType = targetType;
		this.targetId = targetId;
		this.materialName = materialName;
		this.dilutionRatio = dilutionRatio;
		this.quantity = quantity;
		this.worker = worker;
		this.memo = memo;
	}

	public static WorkRecord movement(
		Long orchidGroupId,
		Long fromBedZoneId,
		Long toBedZoneId,
		String worker,
		String memo
	) {
		WorkRecord workRecord = new WorkRecord("위치 이동", LocalDate.now(), "ORCHID_GROUP", orchidGroupId, worker, memo);
		workRecord.fromBedZoneId = fromBedZoneId;
		workRecord.toBedZoneId = toBedZoneId;
		return workRecord;
	}

	public Long getId() {
		return id;
	}

	public String getWorkType() {
		return workType;
	}

	public LocalDate getWorkDate() {
		return workDate;
	}

	public String getTargetType() {
		return targetType;
	}

	public Long getTargetId() {
		return targetId;
	}

	public Long getFromBedZoneId() {
		return fromBedZoneId;
	}

	public Long getToBedZoneId() {
		return toBedZoneId;
	}

	public String getMaterialName() {
		return materialName;
	}

	public String getDilutionRatio() {
		return dilutionRatio;
	}

	public String getQuantity() {
		return quantity;
	}

	public String getWorker() {
		return worker;
	}

	public String getMemo() {
		return memo;
	}
}
