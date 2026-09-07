package com.greenhouse.backend.work.domain.operation;

import com.greenhouse.backend.common.domain.BaseEntity;
import com.greenhouse.backend.work.domain.effect.WorkEffectKind;
import com.greenhouse.backend.work.domain.target.WorkTargetReferenceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "work_types")
public class WorkType extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "work_types_id_seq")
	@SequenceGenerator(name = "work_types_id_seq", sequenceName = "work_types_id_seq", allocationSize = 50)
	private Long id;

	@Column(nullable = false, unique = true, length = 50)
	private String code;

	@Column(nullable = false, length = 50)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private WorkTypeTemplate template;

	@Column(name = "is_default", nullable = false)
	private boolean defaultType;

	@Column(name = "is_system", nullable = false)
	private boolean systemType;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	public WorkType(
			String code,
			String name,
			WorkTypeTemplate template,
			boolean defaultType,
			boolean systemType,
			boolean active,
			int sortOrder) {
		this.code = code;
		this.name = name;
		this.template = template;
		this.defaultType = defaultType;
		this.systemType = systemType;
		this.active = active;
		this.sortOrder = sortOrder;
	}

	public void update(String name, WorkTypeTemplate template, boolean active) {
		if (!systemType) {
			this.name = name;
			this.template = template;
			this.active = active;
		}
	}

	public void changeSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
	}

	public WorkTypeDefinition definition() {
		return WorkTypeDefinition.forCode(code);
	}

	public boolean isManualCreateAllowed() {
		return active && !systemType && definition().allowsManualRegistration()
				&& effectKind() == WorkEffectKind.RECORD_ONLY;
	}

	public boolean isSettingsEditable() {
		return !systemType && definition().allowsManualRegistration();
	}

	public boolean isPeriodOperationAllowed() {
		return active && (isManualCreateAllowed() || definition().supportsPeriodPlanning());
	}

	public List<WorkRegistrationMode> registrationModes() {
		return active && (isManualCreateAllowed() || definition().hasDedicatedRegistration())
				? List.of(WorkRegistrationMode.RECORD, WorkRegistrationMode.PLAN) : List.of();
	}

	public WorkTypeWorkflow workflow() {
		return definition().workflow();
	}

	public WorkTargetReferenceType registrationTargetSource() {
		return definition().targetSource();
	}

	public WorkEffectKind effectKind() {
		return template.effectKind();
	}

	public String handlerCode() {
		return definition().handlerCode(template);
	}
}
