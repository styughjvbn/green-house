package com.greenhouse.backend.work.application.effect;

import com.greenhouse.backend.work.domain.effect.WorkAppliedEffect;
import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroup;
import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroupRelationType;
import com.greenhouse.backend.work.dto.effect.StructureChangeLineageEffectView;
import com.greenhouse.backend.work.dto.effect.StructureChangeLineageGroupView;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StructureChangeLineageQueryService {

	private static final Set<String> HANDLER_CODES = Set.of("MOVEMENT", "REPOT", "DIVIDE", "MERGE");

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
		return HANDLER_CODES.contains(effect.getHandlerCode())
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

	private Map<Long, Integer> sourceQuantities(Map<String, Object> commandDetails) {
		return quantityMap(commandDetails.get("sources"), "sourceOrchidGroupId", "inputQuantity");
	}

	private Map<Long, Integer> resultQuantities(Map<String, Object> resultDetails) {
		Map<Long, Integer> quantities = quantityMap(resultDetails.get("results"), "orchidGroupId", "quantity");
		if (!quantities.isEmpty()) {
			return quantities;
		}
		Long resultId = longValue(resultDetails.get("resultOrchidGroupId"));
		Integer totalInput = integerValue(resultDetails.get("totalInputQuantity"));
		Integer loss = integerValue(resultDetails.get("lossQuantity"));
		if (resultId != null && totalInput != null) {
			quantities.put(resultId, totalInput - (loss == null ? 0 : loss));
		}
		return quantities;
	}

	private Map<Long, Integer> quantityMap(Object value, String idKey, String quantityKey) {
		Map<Long, Integer> quantities = new LinkedHashMap<>();
		if (!(value instanceof List<?> rows)) {
			return quantities;
		}
		for (Object rowValue : rows) {
			if (!(rowValue instanceof Map<?, ?> row)) continue;
			Long id = longValue(row.get(idKey));
			Integer quantity = integerValue(row.get(quantityKey));
			if (id != null && quantity != null) {
				quantities.put(id, quantity);
			}
		}
		return quantities;
	}

	private Long longValue(Object value) {
		return value instanceof Number number ? number.longValue() : null;
	}

	private Integer integerValue(Object value) {
		return value instanceof Number number ? number.intValue() : null;
	}
}
