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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
		name = "orchid_group_mutation_entries",
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uk_orchid_group_mutation_entry_group",
						columnNames = {"mutation_id", "orchid_group_id"}),
				@UniqueConstraint(
						name = "uk_orchid_group_mutation_entry_revision",
						columnNames = {"orchid_group_id", "state_revision_after"})
		})
public class OrchidGroupMutationEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orchid_group_mutation_entries_id_seq")
	@SequenceGenerator(
			name = "orchid_group_mutation_entries_id_seq",
			sequenceName = "orchid_group_mutation_entries_id_seq",
			allocationSize = 50)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "mutation_id", nullable = false)
	private OrchidGroupMutation mutation;

	@Column(name = "orchid_group_id", nullable = false)
	private Long orchidGroupId;

	@Enumerated(EnumType.STRING)
	@Column(name = "entry_kind", nullable = false, length = 20)
	private OrchidGroupMutationEntryKind entryKind;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrchidGroupMutationEntryRole role;

	@Column(name = "state_revision_before")
	private Long stateRevisionBefore;

	@Column(name = "state_revision_after")
	private Long stateRevisionAfter;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "before_state", columnDefinition = "jsonb")
	private OrchidGroupStateSnapshot beforeState;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "after_state", columnDefinition = "jsonb")
	private OrchidGroupStateSnapshot afterState;

	@Column(name = "migration_run_id")
	private Long migrationRunId;

	private OrchidGroupMutationEntry(
			OrchidGroupMutation mutation,
			Long orchidGroupId,
			OrchidGroupMutationEntryKind entryKind,
			OrchidGroupMutationEntryRole role,
			Long stateRevisionBefore,
			Long stateRevisionAfter,
			OrchidGroupStateSnapshot beforeState,
			OrchidGroupStateSnapshot afterState,
			Long migrationRunId) {
		if (mutation == null || orchidGroupId == null || entryKind == null || role == null) {
			throw new IllegalArgumentException("Mutation entry 필수 값이 누락되었습니다.");
		}
		validateState(entryKind, stateRevisionBefore, stateRevisionAfter, beforeState, afterState, migrationRunId);
		this.mutation = mutation;
		this.orchidGroupId = orchidGroupId;
		this.entryKind = entryKind;
		this.role = role;
		this.stateRevisionBefore = stateRevisionBefore;
		this.stateRevisionAfter = stateRevisionAfter;
		this.beforeState = beforeState;
		this.afterState = afterState;
		this.migrationRunId = migrationRunId;
	}

	public static OrchidGroupMutationEntry baseline(
			OrchidGroupMutation mutation,
			Long orchidGroupId,
			OrchidGroupStateSnapshot afterState) {
		return new OrchidGroupMutationEntry(
				mutation, orchidGroupId, OrchidGroupMutationEntryKind.BASELINE,
				OrchidGroupMutationEntryRole.AFFECTED, null, 0L, null, afterState, null);
	}

	public static OrchidGroupMutationEntry created(
			OrchidGroupMutation mutation,
			Long orchidGroupId,
			OrchidGroupMutationEntryRole role,
			OrchidGroupStateSnapshot afterState) {
		return new OrchidGroupMutationEntry(
				mutation, orchidGroupId, OrchidGroupMutationEntryKind.CREATE,
				role, null, 1L, null, afterState, null);
	}

	public static OrchidGroupMutationEntry changed(
			OrchidGroupMutation mutation,
			Long orchidGroupId,
			OrchidGroupMutationEntryRole role,
			long stateRevisionBefore,
			OrchidGroupStateSnapshot beforeState,
			OrchidGroupStateSnapshot afterState) {
		return new OrchidGroupMutationEntry(
				mutation, orchidGroupId, OrchidGroupMutationEntryKind.CHANGE,
				role, stateRevisionBefore, stateRevisionBefore + 1, beforeState, afterState, null);
	}

	public static OrchidGroupMutationEntry historical(
			OrchidGroupMutation mutation,
			Long migrationRunId,
			Long orchidGroupId,
			OrchidGroupMutationEntryRole role) {
		return new OrchidGroupMutationEntry(
				mutation, orchidGroupId, OrchidGroupMutationEntryKind.HISTORICAL,
				role, null, null, null, null, migrationRunId);
	}

	private void validateState(
			OrchidGroupMutationEntryKind kind,
			Long before,
			Long after,
			OrchidGroupStateSnapshot beforeState,
			OrchidGroupStateSnapshot afterState,
			Long migrationRunId) {
		boolean valid = switch (kind) {
			case BASELINE -> before == null && Long.valueOf(0).equals(after)
					&& beforeState == null && afterState != null && migrationRunId == null;
			case CREATE -> before == null && Long.valueOf(1).equals(after)
					&& beforeState == null && afterState != null && migrationRunId == null;
			case CHANGE -> before != null && before >= 0 && Long.valueOf(before + 1).equals(after)
					&& beforeState != null && afterState != null && migrationRunId == null;
			case HISTORICAL -> before == null && after == null && beforeState == null
					&& afterState == null && migrationRunId != null;
		};
		if (!valid) {
			throw new IllegalArgumentException("Mutation entry revision이 entry kind와 일치하지 않습니다.");
		}
	}
}
