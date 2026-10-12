package com.greenhouse.backend.sales.document.application;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.document.api.DirectDocumentChangeApi;
import com.greenhouse.backend.sales.document.domain.SalesSlip;
import com.greenhouse.backend.sales.document.repository.SalesSlipRepository;
import com.greenhouse.backend.sales.partner.api.BusinessPartnerLockApi;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DirectDocumentChangeService implements DirectDocumentChangeApi {
  private final SalesSlipRepository documents;
  private final BusinessPartnerLockApi partners;

  @Transactional(readOnly = true)
  public Target find(Long id) {
    return target(documents.findById(id).orElseThrow(() -> missing()));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Target lock(Long id) {
    var partner = documents.findPartnerId(id).orElseThrow(() -> missing());
    partners.lockAll(List.of(partner));
    var document = documents.findForUpdateById(id).orElseThrow(() -> missing());
    if (!partner.equals(document.getPartnerId()))
      throw new ConflictException("SALES_PARTNER_CHANGED_RETRY", "거래처가 변경되었습니다. 다시 조회해 주세요.");
    return target(document);
  }

  private Target target(SalesSlip document) {
    if (document.getSalesType() != SalesType.DIRECT)
      throw new ConflictException(
          "DIRECT_CHANGE_TARGET_REQUIRED", "일반 판매 전표에서만 가격 조정·반품을 기록할 수 있습니다.");
    return new Target(
        document.getId(),
        document.getPartnerId(),
        document.getSaleDate(),
        document.canRecordDirectChange(),
        document.getItems().stream()
            .map(i -> new Item(i.getId(), i.getItemName(), i.getSpec()))
            .toList());
  }

  private NotFoundException missing() {
    return new NotFoundException("판매 전표를 찾을 수 없습니다.");
  }
}
