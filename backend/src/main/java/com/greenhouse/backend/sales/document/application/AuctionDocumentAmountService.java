package com.greenhouse.backend.sales.document.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.document.api.AuctionDocumentAmountApi;
import com.greenhouse.backend.sales.document.domain.SalesSlipItem;
import com.greenhouse.backend.sales.document.repository.SalesSlipRepository;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class AuctionDocumentAmountService implements AuctionDocumentAmountApi {
  private final SalesSlipRepository documents;
  private final SalesSlipAggregateLoader loader;

  @Override
  public Optional<Target> lockForLot(Long lotId) {
    return documents
        .findAuctionDocumentIdByLotId(lotId)
        .map(
            id -> {
              // Match document cancellation: partner -> document -> lot, never lot -> document.
              var document = loader.getForUpdate(id);
              var lotIds =
                  document.getItems().stream()
                      .map(SalesSlipItem::getAuctionShipmentLotId)
                      .filter(Objects::nonNull)
                      .distinct()
                      .toList();
              if (document.getSalesType() != SalesType.AUCTION || !lotIds.contains(lotId))
                throw new NotFoundException("경매 전표의 lot 연결을 찾을 수 없습니다.");
              return new Target(id, lotIds);
            });
  }

  @Override
  public void updateGrossAmount(Long documentId, long grossAmount) {
    documents.findById(documentId).orElseThrow().applyAuctionGrossProjection(grossAmount);
  }
}
