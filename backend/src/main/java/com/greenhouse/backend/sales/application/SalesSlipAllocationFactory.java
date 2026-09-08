package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.farm.application.orchid.OrchidGroupReader;
import com.greenhouse.backend.farm.application.orchid.OrchidGroupState;
import com.greenhouse.backend.sales.application.command.SalesSlipAllocationInput;
import com.greenhouse.backend.sales.application.command.SalesSlipItemInput;
import com.greenhouse.backend.sales.domain.SalesOrchidSnapshotType;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalesSlipAllocationFactory {

	private final OrchidGroupReader orchidGroupReader;
	private final Clock clock;

	public List<SalesSlipItem> createItems(List<SalesSlipItemInput> requests) {
		requests.forEach(this::validateAllocationSum);
		List<Long> orchidGroupIds = requests.stream()
				.flatMap(request -> request.allocations().stream())
				.map(SalesSlipAllocationInput::orchidGroupId)
				.distinct()
				.sorted()
				.toList();
		var orchidGroups = orchidGroupReader.lockStates(orchidGroupIds);

		LocalDateTime capturedAt = TimeConfig.utcNow(clock);
		return requests.stream().map(request -> createItem(request, orchidGroups, capturedAt)).toList();
	}

	private SalesSlipItem createItem(
			SalesSlipItemInput request,
			Map<Long, OrchidGroupState> orchidGroups,
			LocalDateTime capturedAt) {
		var item = new SalesSlipItem(
				null,
				SalesTextNormalizer.required(request.itemName()),
				SalesTextNormalizer.normalize(request.genus()),
				SalesTextNormalizer.normalize(request.spec()),
				request.quantity(),
				request.unitPrice(),
				SalesTextNormalizer.normalize(request.memo()));
		for (SalesSlipAllocationInput allocationRequest : mergeAllocations(request.allocations())) {
			OrchidGroupState orchidGroup = orchidGroups.get(allocationRequest.orchidGroupId());
			validateItemVariety(request, orchidGroup);
			item.addAllocation(createAllocation(orchidGroup, allocationRequest.quantity(), capturedAt));
		}
		return item;
	}

	private SalesSlipItemAllocation createAllocation(
			OrchidGroupState orchidGroup,
			Integer allocatedQuantity,
			LocalDateTime capturedAt) {
		SalesSlipItemAllocation allocation = new SalesSlipItemAllocation(orchidGroup.id(), allocatedQuantity);
		SalesSlipAllocationBatch.captureSnapshot(allocation, SalesOrchidSnapshotType.CREATION, capturedAt, orchidGroup);
		return allocation;
	}

	private List<SalesSlipAllocationInput> mergeAllocations(List<SalesSlipAllocationInput> allocations) {
		Map<Long, Integer> quantities = new LinkedHashMap<>();
		allocations.forEach(allocation -> quantities.merge(allocation.orchidGroupId(), allocation.quantity(), Integer::sum));
		return quantities.entrySet().stream()
				.map(entry -> new SalesSlipAllocationInput(entry.getKey(), entry.getValue())).toList();
	}

	private void validateAllocationSum(SalesSlipItemInput request) {
		if (request.allocations() == null || request.allocations().isEmpty()) {
			throw new IllegalArgumentException("판매 품목에는 하나 이상의 난 묶음 배분이 필요합니다.");
		}
		int allocatedQuantity = request.allocations().stream().mapToInt(SalesSlipAllocationInput::quantity).sum();
		if (allocatedQuantity != request.quantity()) {
			throw new IllegalArgumentException("난 묶음 배분 합계는 품목 수량과 같아야 합니다.");
		}
	}

	private void validateItemVariety(SalesSlipItemInput request, OrchidGroupState orchidGroup) {
		String itemName = SalesTextNormalizer.required(request.itemName());
		if (!itemName.equals(orchidGroup.varietyName())) {
			throw new IllegalArgumentException("난 묶음 품종과 판매 품목명이 일치하지 않습니다.");
		}
	}
}
