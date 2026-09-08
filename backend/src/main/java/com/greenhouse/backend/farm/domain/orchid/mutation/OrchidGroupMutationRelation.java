package com.greenhouse.backend.farm.domain.orchid.mutation;

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
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "orchid_group_mutation_relations",
		uniqueConstraints = @UniqueConstraint(name = "uk_orchid_group_mutation_relation",
				columnNames = { "mutation_id", "related_mutation_id", "relation_type" }))
public class OrchidGroupMutationRelation {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orchid_group_mutation_relations_id_seq")
	@SequenceGenerator(name = "orchid_group_mutation_relations_id_seq",
			sequenceName = "orchid_group_mutation_relations_id_seq", allocationSize = 50)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "mutation_id", nullable = false)
	private OrchidGroupMutation mutation;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "related_mutation_id", nullable = false)
	private OrchidGroupMutation relatedMutation;

	@Enumerated(EnumType.STRING)
	@Column(name = "relation_type", nullable = false, length = 20)
	private OrchidGroupMutationRelationType relationType;

	public OrchidGroupMutationRelation(OrchidGroupMutation mutation, OrchidGroupMutation relatedMutation,
			OrchidGroupMutationRelationType relationType) {
		if (mutation == null || relatedMutation == null || relationType == null) {
			throw new IllegalArgumentException("Mutation 관계 필수 값이 누락되었습니다.");
		}
		if (mutation == relatedMutation
				|| mutation.getId() != null && mutation.getId().equals(relatedMutation.getId())) {
			throw new IllegalArgumentException("Mutation은 자기 자신과 관계를 맺을 수 없습니다.");
		}
		this.mutation = mutation;
		this.relatedMutation = relatedMutation;
		this.relationType = relationType;
	}

}
