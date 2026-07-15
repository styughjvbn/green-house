package com.greenhouse.backend.work.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "work_types")
public class WorkType extends BaseEntity {

	public static final String MOVEMENT_CODE = "MOVEMENT";
	public static final String INBOUND_CODE = "INBOUND";
	public static final String POTTING_CODE = "POTTING";
	public static final String MULTI_CREATE_CODE = "MULTI_CREATE";
	public static final String REPOT_CODE = "REPOT";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
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

	public boolean isManualCreateAllowed() {
		return active
				&& !systemType
				&& !INBOUND_CODE.equals(code)
				&& !POTTING_CODE.equals(code);
	}

	public boolean isSettingsEditable() {
		return !systemType
				&& !INBOUND_CODE.equals(code)
				&& !POTTING_CODE.equals(code);
	}

	public WorkEffectKind effectKind() {
		return switch (template) {
			case PESTICIDE, FERTILIZER, CLEANUP, STATUS, MEMO -> WorkEffectKind.RECORD_ONLY;
			case MOVEMENT -> WorkEffectKind.ATTRIBUTE_CHANGE;
			case REPOT, MULTI_CREATE -> WorkEffectKind.STRUCTURE_CHANGE;
		};
	}

	public String handlerCode() {
		return switch (template) {
			case PESTICIDE, FERTILIZER, CLEANUP, STATUS, MEMO -> "RECORD_ONLY";
			case MOVEMENT -> "MOVE";
			case REPOT -> "REPOT";
			case MULTI_CREATE -> "MULTI_CREATE";
		};
	}
}
