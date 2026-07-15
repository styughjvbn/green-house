package com.greenhouse.backend.work.domain;

import com.greenhouse.backend.farm.domain.OrchidGroup;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "work_effect_orchid_groups")
public class WorkEffectOrchidGroup {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "work_applied_effect_id", nullable = false)
	private WorkAppliedEffect workAppliedEffect;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "orchid_group_id", nullable = false)
	private OrchidGroup orchidGroup;

	@Enumerated(EnumType.STRING)
	@Column(name = "relation_type", nullable = false, length = 30)
	private WorkEffectOrchidGroupRelationType relationType;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public WorkEffectOrchidGroup(
			WorkAppliedEffect workAppliedEffect,
			OrchidGroup orchidGroup,
			WorkEffectOrchidGroupRelationType relationType) {
		this.workAppliedEffect = workAppliedEffect;
		this.orchidGroup = orchidGroup;
		this.relationType = relationType;
	}
}
