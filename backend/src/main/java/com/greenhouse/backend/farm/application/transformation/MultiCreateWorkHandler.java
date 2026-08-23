package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupCommandService;
import com.greenhouse.backend.farm.application.orchid.mutation.CreateOrchidGroupMutationItem;
import com.greenhouse.backend.farm.application.orchid.mutation.CreateOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationRoutingPolicy;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationShadowService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationSources;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.collection.OrchidGroupCollection;
import com.greenhouse.backend.farm.domain.collection.OrchidGroupCollectionMember;
import com.greenhouse.backend.farm.dto.transformation.MultiCreateWorkOperationRequest;
import com.greenhouse.backend.farm.repository.collection.OrchidGroupCollectionMemberRepository;
import com.greenhouse.backend.farm.repository.collection.OrchidGroupCollectionRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectHandler;
import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import com.greenhouse.backend.work.application.effect.WorkMutationLink;
import com.greenhouse.backend.work.domain.effect.WorkEffectKind;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.target.WorkOperationTarget;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * ORCHID-CUTOVER: LEGACY_RETIRE — Engine 경로와 전환 후 제거할 직접 다중 생성 분기를 함께 가진다.
 * Removal gate: 운영 ACTIVE 안정화 및 writer inventory 승인.
 */
@Component
public class MultiCreateWorkHandler implements WorkEffectHandler {

	private final OrchidGroupCommandService orchidGroupCommandService;
	private final OrchidGroupCollectionRepository collectionRepository;
	private final OrchidGroupCollectionMemberRepository memberRepository;
	private final OrchidGroupRepository orchidGroupRepository;
	private final OrchidGroupMutationEngine mutationEngine;
	private final OrchidGroupMutationRoutingPolicy mutationRoutingPolicy;
	private final OrchidGroupMutationShadowService mutationShadowService;

	public MultiCreateWorkHandler(
			OrchidGroupCommandService orchidGroupCommandService,
			OrchidGroupCollectionRepository collectionRepository,
			OrchidGroupCollectionMemberRepository memberRepository,
			OrchidGroupRepository orchidGroupRepository,
			OrchidGroupMutationEngine mutationEngine,
			OrchidGroupMutationRoutingPolicy mutationRoutingPolicy,
			OrchidGroupMutationShadowService mutationShadowService) {
		this.orchidGroupCommandService = orchidGroupCommandService;
		this.collectionRepository = collectionRepository;
		this.memberRepository = memberRepository;
		this.orchidGroupRepository = orchidGroupRepository;
		this.mutationEngine = mutationEngine;
		this.mutationRoutingPolicy = mutationRoutingPolicy;
		this.mutationShadowService = mutationShadowService;
	}

	@Override public String supports() { return "MULTI_CREATE"; }
	@Override public WorkEffectKind effectKind() { return WorkEffectKind.STRUCTURE_CHANGE; }

	@Override
	public WorkExecutionResult execute(
			WorkOperation operation, WorkOperationTarget target, WorkEffectCommand command) {
		if (target != null) throw new IllegalArgumentException("다중 생성 작업에는 원본 난 묶음 대상이 없어야 합니다.");
		MultiCreateWorkOperationRequest request = command.payloadAs(MultiCreateWorkOperationRequest.class);
		validateCollections(request);
		WorkMutationLink mutationLink = null;
		List<OrchidGroup> groups;
		var mutationCommand = mutationRoutingPolicy.usesMutationContract()
				? new CreateOrchidGroupsMutationCommand(
				OrchidGroupMutationSources.work(operation.getId(), command.effectKey()),
				request.rows().stream()
						.map(row -> new CreateOrchidGroupMutationItem(
								row.orchidGroup().bedZoneId(),
								new OrchidGroupMutationDetails(
										row.orchidGroup().varietyId(),
										row.orchidGroup().quantity(),
										row.orchidGroup().potSize(),
										row.orchidGroup().ageYear(),
										row.orchidGroup().status(),
										row.orchidGroup().placementType(),
										row.orchidGroup().trayCount(),
										row.orchidGroup().splitPlacementAllowed(),
										row.orchidGroup().startPosition(),
										row.orchidGroup().endPosition(),
										row.orchidGroup().memo())))
						.toList(),
				operation.getPlannedStartDate(),
				operation.getMemo())
				: null;
		if (mutationRoutingPolicy.routesToEngine()) {
			var mutation = mutationEngine.createMany(mutationCommand);
			List<Long> groupIds = mutation.entries().stream()
					.map(entry -> entry.orchidGroupId())
					.toList();
			var groupsById = orchidGroupRepository.findAllById(groupIds).stream()
					.collect(java.util.stream.Collectors.toMap(OrchidGroup::getId, group -> group));
			groups = groupIds.stream().map(groupsById::get).toList();
			mutationLink = new WorkMutationLink(mutation.mutationId(), mutation.correlationId());
		} else {
			var shadowPlan = mutationShadowService.prepare(mutationCommand);
			groups = request.rows().stream()
					.map(row -> orchidGroupCommandService.createEntity(row.orchidGroup()))
					.toList();
			mutationShadowService.completeCreated(
					shadowPlan, groups.stream().map(OrchidGroup::getId).toList());
		}
		for (int index = 0; index < groups.size(); index++) {
			OrchidGroup group = groups.get(index);
			var row = request.rows().get(index);
			Set<Long> collectionIds = row.collectionIds() == null ? Set.of() : row.collectionIds();
			memberRepository.saveAll(collectionIds.stream()
					.map(id -> new OrchidGroupCollectionMember(id, group.getId(), command.worker())).toList());
		}
		var details = new LinkedHashMap<String, Object>();
		details.put("createdCount", groups.size());
		details.put("createdOrchidGroupIds", groups.stream().map(OrchidGroup::getId).toList());
		return new WorkExecutionResult(
				"MULTI_CREATE",
				details,
				groups.stream().map(OrchidGroup::getId).toList(),
				mutationLink);
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
