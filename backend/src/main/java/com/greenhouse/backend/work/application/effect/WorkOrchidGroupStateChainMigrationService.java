package com.greenhouse.backend.work.application.effect;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.domain.effect.WorkAppliedEffect;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ORCHID-CUTOVER: TRANSITION_ONLY — legacy Work 효과를 complete state-chain Mutation에 연결한다.
 * Removal gate: 운영 cutover 완료 및 사후 복구 도구 보존 정책 확정.
 */
@Service
@RequiredArgsConstructor
public class WorkOrchidGroupStateChainMigrationService {

	private final WorkAppliedEffectRepository effectRepository;

	@Transactional(readOnly = true)
	public Map<Long, WorkStateChainMutationSource> resolveSources(Collection<Long> effectIds) {
		if (effectIds == null || effectIds.isEmpty()) {
			return Map.of();
		}
		List<WorkAppliedEffect> effects = effectRepository.findAllById(effectIds);
		if (effects.size() != effectIds.size()) {
			throw new NotFoundException("State-chain manifest의 Work 효과를 모두 찾을 수 없습니다.");
		}
		Map<Long, WorkStateChainMutationSource> result = new HashMap<>();
		for (WorkAppliedEffect effect : effects) {
			result.put(effect.getId(), new WorkStateChainMutationSource(
					effect.getId(), effect.getWorkOperation().getId(), effect.getEffectKey()));
		}
		return Map.copyOf(result);
	}

	@Transactional
	public void linkMutations(List<WorkStateChainMutationLinkCommand> commands) {
		if (commands == null || commands.isEmpty()) {
			return;
		}
		Map<Long, WorkStateChainMutationLinkCommand> byEffectId = new HashMap<>();
		for (WorkStateChainMutationLinkCommand command : commands) {
			if (byEffectId.putIfAbsent(command.workEffectId(), command) != null) {
				throw new IllegalArgumentException("Work state-chain 연결 effect ID가 중복됩니다.");
			}
		}
		List<WorkAppliedEffect> effects = effectRepository.findAllById(byEffectId.keySet());
		if (effects.size() != byEffectId.size()) {
			throw new NotFoundException("State-chain Mutation에 연결할 Work 효과를 모두 찾을 수 없습니다.");
		}
		effects.stream().sorted(Comparator.comparing(WorkAppliedEffect::getId)).forEach(effect -> {
			WorkStateChainMutationLinkCommand command = byEffectId.get(effect.getId());
			effect.linkMutation(command.mutationId(), command.correlationId());
		});
	}
}
