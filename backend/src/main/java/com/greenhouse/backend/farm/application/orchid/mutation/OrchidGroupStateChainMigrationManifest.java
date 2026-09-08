package com.greenhouse.backend.farm.application.orchid.mutation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryKind;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryRole;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupStateSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * ORCHID-CUTOVER: RECOVERY — profiler 결과를 정상 revision chain으로 적재하는 고정 계약이다. Removal gate:
 * V20 백업 복구 절차를 대체하는 도구가 검증될 때까지 보존.
 */
public record OrchidGroupStateChainMigrationManifest(@JsonProperty("manifest_schema_version") int manifestSchemaVersion,
		@JsonProperty("generated_from") String generatedFrom, @JsonProperty("migration_ready") boolean migrationReady,
		@JsonProperty("blocking_issues") List<Map<String, Object>> blockingIssues, Map<String, Object> provenance,
		@JsonProperty("operational_profile") Map<String, Object> operationalProfile, List<Mutation> mutations) {

	public OrchidGroupStateChainMigrationManifest {
		blockingIssues = blockingIssues == null ? List.of() : List.copyOf(blockingIssues);
		provenance = provenance == null ? Map.of() : Map.copyOf(provenance);
		operationalProfile = operationalProfile == null ? Map.of() : Map.copyOf(operationalProfile);
		mutations = mutations == null ? List.of() : List.copyOf(mutations);
	}

	public record Mutation(@JsonProperty("mutation_key") String mutationKey,
			@JsonProperty("mutation_type") OrchidGroupMutationType mutationType,
			@JsonProperty("source_type") String sourceType, @JsonProperty("source_reference") String sourceReference,
			@JsonProperty("occurred_at") Instant occurredAt,
			@JsonProperty("effective_business_date") LocalDate effectiveBusinessDate, String reason,
			Map<String, Object> evidence, List<Entry> entries) {

		public Mutation {
			evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
			entries = entries == null ? List.of() : List.copyOf(entries);
		}
	}

	public record Entry(@JsonProperty("orchid_group_id") Long orchidGroupId,
			@JsonProperty("entry_kind") OrchidGroupMutationEntryKind entryKind, OrchidGroupMutationEntryRole role,
			@JsonProperty("revision_before") Long revisionBefore, @JsonProperty("revision_after") Long revisionAfter,
			@JsonProperty("before_state") OrchidGroupStateSnapshot beforeState,
			@JsonProperty("after_state") OrchidGroupStateSnapshot afterState) {
	}
}
