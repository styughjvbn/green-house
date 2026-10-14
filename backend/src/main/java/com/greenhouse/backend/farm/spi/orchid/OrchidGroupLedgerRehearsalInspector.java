package com.greenhouse.backend.farm.spi.orchid;

import com.greenhouse.backend.farm.api.orchid.verification.OrchidGroupLedgerReconciliationGroup;
import com.greenhouse.backend.farm.api.orchid.verification.OrchidGroupLedgerReconciliationIssue;
import java.util.List;

public interface OrchidGroupLedgerRehearsalInspector {

  List<OrchidGroupLedgerReconciliationIssue> inspect(
      List<OrchidGroupLedgerReconciliationGroup> groups);
}
