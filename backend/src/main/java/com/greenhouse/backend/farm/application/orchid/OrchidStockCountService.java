package com.greenhouse.backend.farm.application.orchid;

import com.greenhouse.backend.common.application.RequestActorProvider;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationFingerprint;
import com.greenhouse.backend.farm.application.orchid.mutation.StockCountOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.dto.orchid.OrchidStockCountContext;
import com.greenhouse.backend.farm.dto.orchid.OrchidStockCountRequest;
import com.greenhouse.backend.farm.dto.orchid.OrchidStockCountResponse;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidStockCountRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrchidStockCountService {

	private final OrchidStockCountRepository counts;

	private final OrchidGroupRepository groups;

	private final OrchidGroupMutationEngine engine;

	private final OrchidGroupMutationFingerprint fingerprints;

	private final RequestActorProvider actors;

	private final Clock clock;

	@Value("${features.stock-count.enabled:false}")
	private boolean enabled;

	@Transactional
	public OrchidStockCountResponse count(Long groupId, OrchidStockCountRequest request) {
		// Missing IDs must be resolved before the receipt FK insert.
		if (!groups.existsById(groupId))
			throw new NotFoundException("난 묶음을 찾을 수 없습니다.");
		String fingerprint = fingerprints.calculate(new Request(groupId, request));
		counts.claim(request.idempotencyKey(), fingerprint, groupId,
				LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), request.countedDate(),
				actors.resolve(request.worker()), request.reason(), request.memo());
		var receipt = counts.findForUpdate(request.idempotencyKey());
		receipt.validate(fingerprint);
		if (receipt.getMutationId() != null)
			return OrchidStockCountResponse.from(receipt);
		if (!enabled)
			throw new ConflictException("FEATURE_ON_HOLD", "실사 수량 조정은 현재 비활성화되어 있습니다.");
		groups.findAllForUpdateByIdIn(List.of(groupId));
		if (!TimeConfig.farmToday(clock).equals(request.countedDate()))
			throw new IllegalArgumentException("실사는 현재 업무일에 확인한 수량만 적용할 수 있습니다.");
		var source = new OrchidGroupMutationSource(OrchidGroupMutationSourceDomain.FARM, "STOCK_COUNT",
				request.idempotencyKey(), "COUNT",
				UUID.nameUUIDFromBytes(("STOCK_COUNT:" + request.idempotencyKey()).getBytes(StandardCharsets.UTF_8)));
		var mutation = engine.stockCount(new StockCountOrchidGroupMutationCommand(source, groupId,
				request.expectedRevision(), request.actualQuantity(), request.countedDate(), request.reason()));
		var entry = mutation.entries().getFirst();
		receipt.complete(entry.beforeState().quantity(), entry.afterState().quantity(), mutation.mutationId(),
				LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
		return OrchidStockCountResponse.from(receipt);
	}

	@Transactional(readOnly = true)
	public OrchidStockCountContext context(Long groupId) {
		var group = groups.findById(groupId).orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
		return new OrchidStockCountContext(groupId, group.getQuantity(), group.getStateRevision(),
				TimeConfig.farmToday(clock), enabled && group.allowsStockCount());
	}

	@Transactional(readOnly = true)
	public Page<OrchidStockCountResponse> history(Long groupId, int page, int size) {
		if (page < 0 || size < 1 || size > 100)
			throw new IllegalArgumentException("페이지는 0 이상, 크기는 1~100이어야 합니다.");
		if (!groups.existsById(groupId))
			throw new NotFoundException("난 묶음을 찾을 수 없습니다.");
		return counts
			.findByOrchidGroupIdAndMutationIdIsNotNullOrderByRecordedAtDescRequestKeyDesc(groupId,
					PageRequest.of(page, size))
			.map(OrchidStockCountResponse::from);
	}

	private record Request(Long groupId, OrchidStockCountRequest request) {
	}

}
