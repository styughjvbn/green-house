package com.greenhouse.backend.sales.document.application;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupQueryApi;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupState;
import com.greenhouse.backend.farm.api.variety.VarietySearchApi;
import com.greenhouse.backend.sales.api.document.SalesOrchidSnapshotType;
import com.greenhouse.backend.sales.document.application.command.SalesSlipAllocationInput;
import com.greenhouse.backend.sales.document.application.command.SalesSlipItemInput;
import com.greenhouse.backend.sales.document.domain.SalesSlip;
import com.greenhouse.backend.sales.document.domain.SalesSlipItem;
import com.greenhouse.backend.sales.document.domain.SalesSlipItemAllocation;
import com.greenhouse.backend.sales.document.spi.DirectDocumentAccountingPort;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalesSlipAllocationFactory {

  private final OrchidGroupQueryApi orchidGroupReader;

  private final Clock clock;

  private final VarietySearchApi varieties;

  public void lockForReplacement(SalesSlip current, List<SalesSlipItemInput> requests) {
    requests.forEach(this::validateAllocationSum);
    orchidGroupReader.lockGroups(
        Stream.concat(
                SalesSlipAllocationBatch.from(current).orchidGroupIds().stream(),
                allocationGroupIds(requests).stream())
            .toList());
  }

  public List<SalesSlipItem> createItems(List<SalesSlipItemInput> requests) {
    return createItems(requests, null);
  }

  public List<SalesSlipItem> createRecordedItems(
      List<SalesSlipItemInput> requests, List<DirectDocumentAccountingPort.PriceSnapshot> prices) {
    // A recorded item must explicitly identify a master variety before any persistence.
    if (requests.stream().anyMatch(request -> request.varietyId() == null))
      throw new IllegalArgumentException("판매 품목의 품종을 선택하세요.");
    // Verify identities in one batch; never fabricate past inventory snapshots.
    var ids =
        requests.stream()
            .map(SalesSlipItemInput::varietyId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    var existing =
        (ids.isEmpty() ? List.<VarietySearchApi.Option>of() : varieties.findOptionsByIds(ids))
            .stream().map(VarietySearchApi.Option::id).collect(Collectors.toSet());
    if (!existing.containsAll(ids)) throw new IllegalArgumentException("선택한 품종이 존재하지 않습니다.");
    return IntStream.range(0, requests.size())
        .mapToObj(
            index ->
                createItem(
                    requests.get(index),
                    Map.of(),
                    null,
                    prices == null ? null : prices.get(index),
                    requests.get(index).varietyId()))
        .toList();
  }

  public List<SalesSlipItem> createItems(
      List<SalesSlipItemInput> requests, List<DirectDocumentAccountingPort.PriceSnapshot> prices) {
    requests.forEach(this::validateAllocationSum);
    var orchidGroups = orchidGroupReader.lockStates(allocationGroupIds(requests));

    LocalDateTime capturedAt = TimeConfig.utcNow(clock);
    return IntStream.range(0, requests.size())
        .mapToObj(
            index ->
                createItem(
                    requests.get(index),
                    orchidGroups,
                    capturedAt,
                    prices == null ? null : prices.get(index),
                    allocatedVarietyId(requests.get(index), orchidGroups)))
        .toList();
  }

  private List<Long> allocationGroupIds(List<SalesSlipItemInput> requests) {
    return requests.stream()
        .flatMap(request -> request.allocations().stream())
        .map(SalesSlipAllocationInput::orchidGroupId)
        .distinct()
        .sorted()
        .toList();
  }

  private SalesSlipItem createItem(
      SalesSlipItemInput request,
      Map<Long, OrchidGroupState> orchidGroups,
      LocalDateTime capturedAt,
      DirectDocumentAccountingPort.PriceSnapshot price,
      Long varietyId) {
    var item =
        price == null
            ? new SalesSlipItem(
                null,
                SalesTextNormalizer.required(request.itemName()),
                SalesTextNormalizer.normalize(request.genus()),
                SalesTextNormalizer.normalize(request.spec()),
                request.quantity(),
                request.unitPrice(),
                SalesTextNormalizer.normalize(request.memo()),
                request.unit(),
                request.boxes())
            : new SalesSlipItem(
                null,
                SalesTextNormalizer.required(request.itemName()),
                SalesTextNormalizer.normalize(request.genus()),
                SalesTextNormalizer.normalize(request.spec()),
                request.quantity(),
                price.unitPrice(),
                price.amount(),
                SalesTextNormalizer.normalize(request.memo()),
                request.unit(),
                request.boxes());
    item.assignVariety(varietyId);
    for (SalesSlipAllocationInput allocationRequest : mergeAllocations(request.allocations())) {
      OrchidGroupState orchidGroup = orchidGroups.get(allocationRequest.orchidGroupId());
      item.addAllocation(createAllocation(orchidGroup, allocationRequest.quantity(), capturedAt));
    }
    return item;
  }

  private SalesSlipItemAllocation createAllocation(
      OrchidGroupState orchidGroup, Integer allocatedQuantity, LocalDateTime capturedAt) {
    SalesSlipItemAllocation allocation =
        new SalesSlipItemAllocation(orchidGroup.id(), allocatedQuantity);
    SalesSlipAllocationBatch.captureSnapshot(
        allocation, SalesOrchidSnapshotType.CREATION, capturedAt, orchidGroup);
    return allocation;
  }

  private List<SalesSlipAllocationInput> mergeAllocations(
      List<SalesSlipAllocationInput> allocations) {
    Map<Long, Integer> quantities = new LinkedHashMap<>();
    allocations.forEach(
        allocation ->
            quantities.merge(allocation.orchidGroupId(), allocation.quantity(), Integer::sum));
    return quantities.entrySet().stream()
        .map(entry -> new SalesSlipAllocationInput(entry.getKey(), entry.getValue()))
        .toList();
  }

  private void validateAllocationSum(SalesSlipItemInput request) {
    if (request.allocations() == null || request.allocations().isEmpty()) {
      throw new IllegalArgumentException("판매 품목에는 하나 이상의 난 묶음 배분이 필요합니다.");
    }
    int allocatedQuantity =
        request.allocations().stream().mapToInt(SalesSlipAllocationInput::quantity).sum();
    if (allocatedQuantity != request.quantity()) {
      throw new IllegalArgumentException("난 묶음 배분 합계는 품목 수량과 같아야 합니다.");
    }
  }

  private Long allocatedVarietyId(SalesSlipItemInput request, Map<Long, OrchidGroupState> groups) {
    var ids = new HashSet<Long>();
    boolean completeIdentity = true;
    for (var allocation : request.allocations()) {
      var group = groups.get(allocation.orchidGroupId());
      if (group == null) throw new IllegalArgumentException("난 묶음을 확인할 수 없습니다.");
      if (request.varietyId() != null) {
        if (!request.varietyId().equals(group.varietyId()))
          throw new IllegalArgumentException("난 묶음과 판매 품목의 품종 ID가 일치하지 않습니다.");
      } else if (!SalesTextNormalizer.required(request.itemName()).equals(group.varietyName())) {
        throw new IllegalArgumentException("난 묶음 품종과 판매 품목명이 일치하지 않습니다.");
      }
      if (group.varietyId() != null) ids.add(group.varietyId());
      else completeIdentity = false;
    }
    if (ids.size() > 1) throw new IllegalArgumentException("한 품목에는 같은 품종의 난 묶음만 연결할 수 있습니다.");
    if (request.varietyId() != null) return request.varietyId();
    return completeIdentity ? ids.stream().findFirst().orElse(null) : null;
  }
}
