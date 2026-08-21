package com.greenhouse.backend.work.application.effect;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.domain.effect.WorkAppliedEffect;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkHistoricalEffectService {

	private static final List<String> STATE_CHANGING_HANDLERS = List.of(
			"DISCARD", "DIVIDE", "MOVE", "MOVEMENT", "POTTING", "REPOT");

	private final WorkAppliedEffectRepository effectRepository;
	private final WorkEffectOrchidGroupRepository linkRepository;

	@Transactional(readOnly = true)
	public List<HistoricalWorkEffect> findAfter(long afterId, Instant sourceCutoff, int limit) {
		if (afterId < 0 || sourceCutoff == null || limit < 1 || limit > 500) {
			throw new IllegalArgumentException("Historical Work 조회 범위가 올바르지 않습니다.");
		}
		List<WorkAppliedEffect> effects = effectRepository.findHistoricalStateEffectsAfter(
				afterId,
				LocalDateTime.ofInstant(sourceCutoff, ZoneOffset.UTC),
				STATE_CHANGING_HANDLERS,
				PageRequest.of(0, limit));
		if (effects.isEmpty()) {
			return List.of();
		}
		Map<Long, List<HistoricalWorkEffectLink>> linksByEffectId = linkRepository
				.findByWorkAppliedEffectIdInOrderByWorkAppliedEffectIdAscIdAsc(
						effects.stream().map(WorkAppliedEffect::getId).toList())
				.stream()
				.collect(Collectors.groupingBy(
						link -> link.getWorkAppliedEffect().getId(),
						Collectors.mapping(
								link -> new HistoricalWorkEffectLink(
										link.getOrchidGroupId(), link.getRelationType()),
								Collectors.toList())));
		return effects.stream()
				.map(effect -> toHistoricalEffect(
						effect,
						linksByEffectId.getOrDefault(effect.getId(), List.of())))
				.toList();
	}

	@Transactional
	public void linkMutation(Long effectId, Long mutationId, UUID correlationId) {
		WorkAppliedEffect effect = effectRepository.findById(effectId)
				.orElseThrow(() -> new NotFoundException("Historical Work 효과를 찾을 수 없습니다."));
		effect.linkMutation(mutationId, correlationId);
	}

	@Transactional
	public void linkMutations(List<WorkHistoricalMutationLinkCommand> commands) {
		if (commands == null || commands.isEmpty()) {
			return;
		}
		Map<Long, WorkHistoricalMutationLinkCommand> commandsByEffectId = new HashMap<>();
		commands.forEach(command -> {
			if (commandsByEffectId.putIfAbsent(command.workEffectId(), command) != null) {
				throw new IllegalArgumentException("Historical Work 효과 연결이 중복됩니다.");
			}
		});
		List<WorkAppliedEffect> effects = effectRepository.findAllById(commandsByEffectId.keySet());
		if (effects.size() != commandsByEffectId.size()) {
			throw new NotFoundException("Historical Work 효과를 모두 찾을 수 없습니다.");
		}
		effects.stream().sorted(java.util.Comparator.comparing(WorkAppliedEffect::getId)).forEach(effect -> {
			WorkHistoricalMutationLinkCommand command = commandsByEffectId.get(effect.getId());
			effect.linkMutation(command.mutationId(), command.correlationId());
		});
	}

	@Transactional(readOnly = true)
	public List<Long> findUnlinkedIds(Instant sourceCutoff) {
		if (sourceCutoff == null) {
			throw new IllegalArgumentException("Historical Work source cutoff이 필요합니다.");
		}
		return effectRepository.findUnlinkedHistoricalStateEffectIds(
				LocalDateTime.ofInstant(sourceCutoff, ZoneOffset.UTC),
				STATE_CHANGING_HANDLERS);
	}

	private HistoricalWorkEffect toHistoricalEffect(
			WorkAppliedEffect effect,
			List<HistoricalWorkEffectLink> links) {
		return new HistoricalWorkEffect(
				effect.getId(),
				effect.getWorkOperation().getId(),
				effect.getEffectKey(),
				effect.getHandlerCode(),
				effect.getAppliedAt().toInstant(ZoneOffset.UTC),
				effect.getCommandDetails(),
				effect.getResultDetails(),
				effect.getMutationId(),
				effect.getCorrelationId(),
				links);
	}
}
