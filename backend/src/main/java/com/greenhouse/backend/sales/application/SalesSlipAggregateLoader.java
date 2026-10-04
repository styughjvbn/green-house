package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.repository.SalesSlipItemAllocationRepository;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Loads the owned collections needed by sales writes after locking their root. */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class SalesSlipAggregateLoader {
  private final SalesSlipRepository salesSlipRepository;
  private final SalesSlipItemAllocationRepository allocationRepository;

  SalesSlip getForUpdate(Long id) {
    var slip =
        salesSlipRepository
            .findForUpdateById(id)
            .orElseThrow(() -> new NotFoundException("판매 전표를 찾을 수 없습니다."));
    if (!slip.getItems().isEmpty()) {
      // Fetch one collection per query; fetching both bags together multiplies rows.
      salesSlipRepository.findItemsWithAllocationsBySalesSlipId(id);
      allocationRepository.findAllWithSnapshotsBySalesSlipIdIn(List.of(id));
    }
    return slip;
  }
}
