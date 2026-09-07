package com.greenhouse.backend.work.application.effect;

import static com.greenhouse.backend.work.application.effect.WorkEffectResults.integerValue;
import static com.greenhouse.backend.work.application.effect.WorkEffectResults.resultQuantities;
import static com.greenhouse.backend.work.application.effect.WorkEffectResults.sourceQuantities;

import com.greenhouse.backend.work.domain.effect.WorkAppliedEffect;
import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroup;
import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroupRelationType;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
import com.greenhouse.backend.work.dto.effect.StructureChangeLineageEffectView;
import com.greenhouse.backend.work.dto.effect.StructureChangeLineageGroupView;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StructureChangeLineageQueryService {

	private final WorkEffectOrchidGroupRepository effectOrchidGroupRepository;

	public List<StructureChangeLineageEffectView> findByOrchidGroupId(Long orchidGroupId) {
		Map<Long, WorkAppliedEffect> effectsById = effectOrchidGroupRepository
				.findByOrchidGroupIdOrderByWorkAppliedEffectAppliedAtDescWorkAppliedEffectIdDesc(orchidGroupId)
				.stream()
				.map(WorkEffectOrchidGroup::getWorkAppliedEffect)
				.filter(this::isStructureChangeExecution)
				.collect(Collectors.toMap(
						WorkAppliedEffect::getId,
						Function.identity(),
						(left, right) -> left,
						LinkedHashMap::new));
		if (effectsById.isEmpty()) {
			return List.of();
		}

		Map<Long, List<WorkEffectOrchidGroup>> linksByEffectId = effectOrchidGroupRepository
				.findByWorkAppliedEffectIdInOrderByWorkAppliedEffectIdAscIdAsc(effectsById.keySet())
				.stream()
				.collect(Collectors.groupingBy(
						link -> link.getWorkAppliedEffect().getId(),
						LinkedHashMap::new,
						Collectors.toList()));

		return effectsById.values().stream()
				.map(effect -> toView(effect, linksByEffectId.getOrDefault(effect.getId(), List.of())))
				.toList();
	}

	private boolean isStructureChangeExecution(WorkAppliedEffect effect) {
		return WorkTypeDefinition.forCode(effect.getHandlerCode()).supportsStructureExecution()
				&& (effect.getEffectKey().startsWith("EXECUTION:")
						|| hasSourceRows(effect.getCommandDetails()));
	}

	private boolean hasSourceRows(Map<String, Object> commandDetails) {
		return commandDetails != null
				&& commandDetails.get("sources") instanceof List<?> sources
				&& !sources.isEmpty();
	}

	private StructureChangeLineageEffectView toView(
			WorkAppliedEffect effect,
			List<WorkEffectOrchidGroup> links) {
		Map<Long, Integer> sourceQuantities = sourceQuantities(effect.getCommandDetails());
		Map<Long, Integer> resultQuantities = resultQuantities(effect.getResultDetails());
		return new StructureChangeLineageEffectView(
				effect.getId(),
				effect.getWorkOperation().getId(),
				effect.getHandlerCode(),
				effect.getAppliedAt(),
				integerValue(effect.getResultDetails().get("lossQuantity")),
				groups(links, WorkEffectOrchidGroupRelationType.SOURCE, sourceQuantities),
				groups(links, WorkEffectOrchidGroupRelationType.RESULT, resultQuantities));
	}

	private List<StructureChangeLineageGroupView> groups(
			List<WorkEffectOrchidGroup> links,
			WorkEffectOrchidGroupRelationType relationType,
			Map<Long, Integer> quantities) {
		var groupIds = links.stream()
				.filter(link -> link.getRelationType() == relationType)
				.map(WorkEffectOrchidGroup::getOrchidGroupId)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		groupIds.addAll(quantities.keySet());
		return groupIds.stream()
				.map(groupId -> new StructureChangeLineageGroupView(groupId, quantities.get(groupId)))
				.toList();
	}

}
