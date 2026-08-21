package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;

public record OrchidGroupHistoryMigrationImportedSource(
		OrchidGroupMutationSource source,
		Long mutationId) {
}
