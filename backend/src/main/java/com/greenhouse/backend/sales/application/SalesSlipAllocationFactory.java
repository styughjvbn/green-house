package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.application.orchid.OrchidGroupReader;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import com.greenhouse.backend.sales.dto.SalesSlipItemAllocationRequest;
import com.greenhouse.backend.sales.dto.SalesSlipItemRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalesSlipAllocationFactory {

	private final OrchidGroupReader orchidGroupReader;

	public List<SalesSlipItem> createItems(List<SalesSlipItemRequest> requests) {
		requests.forEach(this::validateAllocationSum);
		List<Long> orchidGroupIds = requests.stream()
				.flatMap(request -> request.allocations().stream())
				.map(SalesSlipItemAllocationRequest::orchidGroupId)
				.distinct()
				.sorted()
				.toList();
		Map<Long, OrchidGroup> orchidGroups = orchidGroupReader.findAllForUpdateByIds(orchidGroupIds).stream()
				.collect(Collectors.toMap(OrchidGroup::getId, Function.identity()));
		if (orchidGroups.size() != orchidGroupIds.size()) {
			throw new NotFoundException("난 묶음을 찾을 수 없습니다.");
		}

		Map<Long, Integer> requestedByOrchidGroupId = requests.stream()
				.flatMap(request -> mergeAllocations(request.allocations()).stream())
				.collect(Collectors.toMap(
						SalesSlipItemAllocationRequest::orchidGroupId,
						SalesSlipItemAllocationRequest::quantity,
						Integer::sum));
		requestedByOrchidGroupId.forEach((orchidGroupId, quantity) -> {
			if (orchidGroups.get(orchidGroupId).getAvailableQuantity() < quantity) {
				throw new IllegalArgumentException("난 묶음 가용 수량이 부족합니다.");
			}
		});
		return requests.stream().map(request -> createItem(request, orchidGroups)).toList();
	}

	private SalesSlipItem createItem(SalesSlipItemRequest request, Map<Long, OrchidGroup> orchidGroups) {
		var item = new SalesSlipItem(
				null,
				SalesTextNormalizer.required(request.itemName()),
				SalesTextNormalizer.normalize(request.genus()),
				SalesTextNormalizer.normalize(request.spec()),
				request.quantity(),
				request.unitPrice(),
				SalesTextNormalizer.normalize(request.memo()));
		for (SalesSlipItemAllocationRequest allocationRequest : mergeAllocations(request.allocations())) {
			OrchidGroup orchidGroup = orchidGroups.get(allocationRequest.orchidGroupId());
			validateItemVariety(request, orchidGroup);
			item.addAllocation(new SalesSlipItemAllocation(orchidGroup, allocationRequest.quantity()));
		}
		return item;
	}

	private List<SalesSlipItemAllocationRequest> mergeAllocations(List<SalesSlipItemAllocationRequest> allocations) {
		List<SalesSlipItemAllocationRequest> merged = new ArrayList<>();
		for (SalesSlipItemAllocationRequest allocation : allocations) {
			int existingIndex = -1;
			for (int index = 0; index < merged.size(); index++) {
				if (merged.get(index).orchidGroupId().equals(allocation.orchidGroupId())) {
					existingIndex = index;
					break;
				}
			}
			if (existingIndex >= 0) {
				var existing = merged.get(existingIndex);
				merged.set(existingIndex, new SalesSlipItemAllocationRequest(
						existing.orchidGroupId(),
						existing.quantity() + allocation.quantity()));
			} else {
				merged.add(allocation);
			}
		}
		return merged;
	}

	private void validateAllocationSum(SalesSlipItemRequest request) {
		if (request.allocations() == null || request.allocations().isEmpty()) {
			throw new IllegalArgumentException("판매 품목에는 하나 이상의 난 묶음 배분이 필요합니다.");
		}
		int allocatedQuantity = request.allocations().stream().mapToInt(SalesSlipItemAllocationRequest::quantity).sum();
		if (allocatedQuantity != request.quantity()) {
			throw new IllegalArgumentException("난 묶음 배분 합계는 품목 수량과 같아야 합니다.");
		}
	}

	private void validateItemVariety(SalesSlipItemRequest request, OrchidGroup orchidGroup) {
		String itemName = SalesTextNormalizer.required(request.itemName());
		if (!itemName.equals(orchidGroup.getVarietyName())) {
			throw new IllegalArgumentException("난 묶음 품종과 판매 품목명이 일치하지 않습니다.");
		}
	}
}
