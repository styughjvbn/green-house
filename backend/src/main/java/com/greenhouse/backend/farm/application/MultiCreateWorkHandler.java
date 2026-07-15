package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.domain.OrchidGroupCollection;
import com.greenhouse.backend.farm.domain.OrchidGroupCollectionMember;
import com.greenhouse.backend.farm.dto.MultiCreateWorkOperationRequest;
import com.greenhouse.backend.farm.repository.OrchidGroupCollectionMemberRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupCollectionRepository;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectHandler;
import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import com.greenhouse.backend.work.domain.WorkEffectKind;
import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkOperationTarget;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class MultiCreateWorkHandler implements WorkEffectHandler {

	private final OrchidGroupCommandService orchidGroupCommandService;
	private final OrchidGroupCollectionRepository collectionRepository;
	private final OrchidGroupCollectionMemberRepository memberRepository;

	public MultiCreateWorkHandler(
			OrchidGroupCommandService orchidGroupCommandService,
			OrchidGroupCollectionRepository collectionRepository,
			OrchidGroupCollectionMemberRepository memberRepository) {
		this.orchidGroupCommandService = orchidGroupCommandService;
		this.collectionRepository = collectionRepository;
		this.memberRepository = memberRepository;
	}

	@Override public String supports() { return "MULTI_CREATE"; }
	@Override public WorkEffectKind effectKind() { return WorkEffectKind.STRUCTURE_CHANGE; }

	@Override
	public WorkExecutionResult execute(
			WorkOperation operation, WorkOperationTarget target, WorkEffectCommand command) {
		if (target != null) throw new IllegalArgumentException("다중 생성 작업에는 원본 난 묶음 대상이 없어야 합니다.");
		MultiCreateWorkOperationRequest request = command.payloadAs(MultiCreateWorkOperationRequest.class);
		validateCollections(request);
		List<OrchidGroup> groups = request.rows().stream().map(row -> {
			OrchidGroup group = orchidGroupCommandService.createEntity(row.orchidGroup());
			Set<Long> collectionIds = row.collectionIds() == null ? Set.of() : row.collectionIds();
			memberRepository.saveAll(collectionIds.stream()
					.map(id -> new OrchidGroupCollectionMember(id, group.getId(), command.worker())).toList());
			return group;
		}).toList();
		var details = new LinkedHashMap<String, Object>();
		details.put("createdCount", groups.size());
		details.put("createdOrchidGroupIds", groups.stream().map(OrchidGroup::getId).toList());
		return new WorkExecutionResult("MULTI_CREATE", details, groups.stream().map(OrchidGroup::getId).toList());
	}

	private void validateCollections(MultiCreateWorkOperationRequest request) {
		Set<Long> ids = new HashSet<>();
		request.rows().forEach(row -> { if (row.collectionIds() != null) ids.addAll(row.collectionIds()); });
		if (ids.isEmpty()) return;
		List<OrchidGroupCollection> collections = collectionRepository.findAllById(ids);
		if (collections.size() != ids.size()) throw new NotFoundException("지정한 사용자 그룹 중 찾을 수 없는 대상이 있습니다.");
		if (collections.stream().anyMatch(OrchidGroupCollection::isArchived)) {
			throw new IllegalArgumentException("보관된 사용자 그룹에는 생성 결과를 추가할 수 없습니다.");
		}
	}
}
