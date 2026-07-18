package com.greenhouse.backend.farm.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "orchid_group_collections")
public class OrchidGroupCollection extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(columnDefinition = "text")
	private String description;

	@Column(length = 200)
	private String purpose;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrchidGroupCollectionStatus status;

	@Column(name = "created_by", length = 100)
	private String createdBy;

	@Version
	@Column(nullable = false)
	private long version;

	public OrchidGroupCollection(String name, String description, String purpose, String createdBy) {
		this.name = name;
		this.description = description;
		this.purpose = purpose;
		this.createdBy = createdBy;
		this.status = OrchidGroupCollectionStatus.ACTIVE;
	}

	public void update(String name, String description, String purpose) {
		if (status == OrchidGroupCollectionStatus.ARCHIVED) {
			throw new IllegalArgumentException("보관된 사용자 그룹은 수정할 수 없습니다.");
		}
		this.name = name;
		this.description = description;
		this.purpose = purpose;
	}

	public void archive() {
		this.status = OrchidGroupCollectionStatus.ARCHIVED;
	}

	public boolean isArchived() {
		return status == OrchidGroupCollectionStatus.ARCHIVED;
	}
}
