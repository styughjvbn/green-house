package com.greenhouse.backend.farm.domain.transformation;

import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "orchid_group_lineage", uniqueConstraints = @UniqueConstraint(
		name = "uk_orchid_group_lineage_operation_groups_relation",
		columnNames = { "work_operation_id", "source_orchid_group_id", "result_orchid_group_id", "relation_type" }))
public class OrchidGroupLineage {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orchid_group_lineage_id_seq")
	@SequenceGenerator(name = "orchid_group_lineage_id_seq", sequenceName = "orchid_group_lineage_id_seq",
			allocationSize = 50)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "source_orchid_group_id", nullable = false)
	private OrchidGroup sourceOrchidGroup;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "result_orchid_group_id", nullable = false)
	private OrchidGroup resultOrchidGroup;

	@Enumerated(EnumType.STRING)
	@Column(name = "relation_type", nullable = false, length = 30)
	private OrchidGroupLineageRelationType relationType;

	@Column(name = "work_operation_id", nullable = false)
	private Long workOperationId;

	@Column(name = "source_quantity", nullable = false)
	private Integer sourceQuantity;

	@Column(name = "result_quantity", nullable = false)
	private Integer resultQuantity;

	@Column(name = "mutation_id")
	private Long mutationId;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public OrchidGroupLineage(OrchidGroup sourceOrchidGroup, OrchidGroup resultOrchidGroup,
			OrchidGroupLineageRelationType relationType, Long workOperationId, Integer sourceQuantity,
			Integer resultQuantity) {
		if (sourceOrchidGroup == resultOrchidGroup
				|| sourceOrchidGroup.getId() != null && sourceOrchidGroup.getId().equals(resultOrchidGroup.getId())) {
			throw new IllegalArgumentException("원본과 결과 난 묶음은 달라야 합니다.");
		}
		validatePositive(sourceQuantity, "원본 수량");
		validatePositive(resultQuantity, "결과 수량");
		if (workOperationId == null) {
			throw new IllegalArgumentException("작업 ID가 필요합니다.");
		}
		this.sourceOrchidGroup = sourceOrchidGroup;
		this.resultOrchidGroup = resultOrchidGroup;
		this.relationType = relationType;
		this.workOperationId = workOperationId;
		this.sourceQuantity = sourceQuantity;
		this.resultQuantity = resultQuantity;
	}

	public void linkMutation(Long mutationId) {
		if (mutationId == null) {
			throw new IllegalArgumentException("계보에 연결할 Mutation ID가 필요합니다.");
		}
		if (this.mutationId != null && !this.mutationId.equals(mutationId)) {
			throw new IllegalStateException("계보는 다른 Mutation으로 변경할 수 없습니다.");
		}
		this.mutationId = mutationId;
	}

	private void validatePositive(Integer quantity, String label) {
		if (quantity == null || quantity < 1) {
			throw new IllegalArgumentException(label + "은 1 이상이어야 합니다.");
		}
	}

}
