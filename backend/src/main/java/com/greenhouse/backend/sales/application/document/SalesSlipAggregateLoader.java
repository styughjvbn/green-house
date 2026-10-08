package com.greenhouse.backend.sales.application.document;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.domain.document.SalesSlip;
import com.greenhouse.backend.sales.partner.api.BusinessPartnerLockApi;
import com.greenhouse.backend.sales.repository.document.SalesSlipItemAllocationRepository;
import com.greenhouse.backend.sales.repository.document.SalesSlipRepository;
import java.util.Collection;
import java.util.HashSet;
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
  private final BusinessPartnerLockApi partnerLock;
  private final SalesSlipItemAllocationRepository allocationRepository;

  SalesSlip getForUpdate(Long id) {
    return getForUpdate(id, List.of());
  }

  SalesSlip getForUpdate(Long id, Collection<Long> additionalPartners) {
    Long partnerId =
        salesSlipRepository
            .findPartnerId(id)
            .orElseThrow(() -> new NotFoundException("판매 전표를 찾을 수 없습니다."));
    var partnerIds = new HashSet<>(additionalPartners);
    partnerIds.add(partnerId);
    partnerLock.lockAll(partnerIds);
    var slip =
        salesSlipRepository
            .findForUpdateById(id)
            .orElseThrow(() -> new NotFoundException("판매 전표를 찾을 수 없습니다."));
    if (!partnerIds.contains(slip.getPartnerId())) {
      throw new ConflictException(
          "SALES_PARTNER_CHANGED_RETRY", "전표의 거래처가 변경되었습니다. 다시 조회한 뒤 처리하세요.");
    }
    if (!slip.getItems().isEmpty()) {
      // Fetch one collection per query; fetching both bags together multiplies rows.
      salesSlipRepository.findItemsWithAllocationsBySalesSlipId(id);
      allocationRepository.findAllWithSnapshotsBySalesSlipIdIn(List.of(id));
    }
    return slip;
  }
}
