package com.greenhouse.backend.sales.application.auction;

import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.application.partner.BusinessPartnerReader;
import com.greenhouse.backend.sales.application.payment.PaymentAllocationReader;
import com.greenhouse.backend.sales.domain.auction.AuctionProceeds;
import com.greenhouse.backend.sales.domain.payment.PaymentTargetType;
import com.greenhouse.backend.sales.dto.auction.AuctionProceedsResponse;
import com.greenhouse.backend.sales.repository.auction.AuctionProceedsRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionProceedsReader {
  private final AuctionProceedsRepository repository;
  private final BusinessPartnerReader partners;
  private final PaymentAllocationReader allocations;
  private final AuctionDataReader results;

  public Set<Long> findReferencedShipmentIds(Collection<Long> ids) {
    if (ids.isEmpty()) return Set.of();
    return Set.copyOf(repository.findReferencedShipmentIds(ids));
  }

  public AuctionProceedsResponse get(Long id) {
    var root =
        repository.findById(id).orElseThrow(() -> new NotFoundException("경매 대금 자료를 찾을 수 없습니다."));
    return assemble(List.of(root)).getFirst();
  }

  public PageResponse<AuctionProceedsResponse> page(Long houseId, int page, int size) {
    var roots =
        repository.findByAuctionHouseIdFilter(
            houseId,
            PageRequest.of(
                Math.max(0, page), Math.clamp(size, 1, 100), Sort.by("id").descending()));
    var values = assemble(roots.getContent());
    return new PageResponse<>(
        values,
        roots.getNumber(),
        roots.getSize(),
        roots.getTotalElements(),
        roots.getTotalPages());
  }

  private List<AuctionProceedsResponse> assemble(List<AuctionProceeds> roots) {
    if (roots.isEmpty()) return List.of();
    var ids = roots.stream().map(AuctionProceeds::getId).toList();
    // A root page is loaded first; collections, identities and cash are then batched.
    var references =
        repository.findResultReferences(ids).stream()
            .collect(
                Collectors.groupingBy(
                    AuctionProceedsRepository.ResultReference::getProceedsId,
                    Collectors.mapping(
                        AuctionProceedsRepository.ResultReference::getResultLineId,
                        Collectors.toList())));
    var details = results.getResults(references.values().stream().flatMap(List::stream).toList());
    var identities =
        partners.getIdentities(roots.stream().map(AuctionProceeds::getAuctionHouseId).toList());
    var owners =
        roots.stream()
            .collect(Collectors.toMap(AuctionProceeds::getId, AuctionProceeds::getAuctionHouseId));
    var cash = allocations.findAll(PaymentTargetType.AUCTION_PROCEEDS, owners);
    return roots.stream()
        .map(
            root -> {
              var paid = cash.get(root.getId());
              boolean review =
                  paid.reviewRequired()
                      || (root.getReceivableAmount() != null
                          && paid.amount().compareTo(BigDecimal.valueOf(root.getReceivableAmount()))
                              > 0);
              var remaining =
                  root.isPaymentTargetReady() ? root.remainingAmount(paid.amount()) : null;
              return new AuctionProceedsResponse(
                  root.getId(),
                  root.getAuctionHouseId(),
                  identities.get(root.getAuctionHouseId()).name(),
                  root.getSourceReference(),
                  root.getReportedGrossAmount(),
                  root.getReceivableAmount(),
                  root.isMatchingConfirmed(),
                  paid.amount(),
                  remaining,
                  review,
                  root.isAllocationAllowed(paid.amount(), review),
                  List.copyOf(references.getOrDefault(root.getId(), List.of())),
                  references.getOrDefault(root.getId(), List.of()).stream()
                      .map(details::get)
                      .toList());
            })
        .toList();
  }
}
