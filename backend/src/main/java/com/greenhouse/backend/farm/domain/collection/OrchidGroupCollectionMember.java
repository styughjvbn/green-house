package com.greenhouse.backend.farm.domain.collection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "orchid_group_collection_members")
public class OrchidGroupCollectionMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "collection_id", nullable = false)
	private Long collectionId;

	@Column(name = "orchid_group_id", nullable = false)
	private Long orchidGroupId;

	@Column(name = "joined_at", nullable = false)
	private LocalDateTime joinedAt;

	@Column(name = "removed_at")
	private LocalDateTime removedAt;

	@Column(name = "created_by", length = 100)
	private String createdBy;

	public OrchidGroupCollectionMember(Long collectionId, Long orchidGroupId, String createdBy) {
		this.collectionId = collectionId;
		this.orchidGroupId = orchidGroupId;
		this.createdBy = createdBy;
		this.joinedAt = LocalDateTime.now(ZoneOffset.UTC);
	}

	public void remove() {
		if (removedAt == null) {
			removedAt = LocalDateTime.now(ZoneOffset.UTC);
		}
	}
}
