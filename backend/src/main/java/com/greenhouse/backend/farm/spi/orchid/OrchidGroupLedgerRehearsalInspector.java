package com.greenhouse.backend.farm.spi.orchid;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupLedgerReconciliationGroup;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupLedgerReconciliationIssue;
import java.util.List;

public interface OrchidGroupLedgerRehearsalInspector {

  List<OrchidGroupLedgerReconciliationIssue> inspect(
      List<OrchidGroupLedgerReconciliationGroup> groups);
}
