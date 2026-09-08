package com.greenhouse.backend.farm.application.orchid.mutation;

import java.util.List;

public interface OrchidGroupLedgerRehearsalInspector {

	List<OrchidGroupLedgerReconciliationIssue> inspect(List<OrchidGroupLedgerReconciliationGroup> groups);

}
