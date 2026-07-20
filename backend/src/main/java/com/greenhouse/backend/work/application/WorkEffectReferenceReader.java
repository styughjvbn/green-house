package com.greenhouse.backend.work.application;

import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WorkEffectReferenceReader {

	private final WorkEffectOrchidGroupRepository workEffectOrchidGroupRepository;

	public boolean existsByOrchidGroupId(Long orchidGroupId) {
		return workEffectOrchidGroupRepository.existsByOrchidGroupId(orchidGroupId);
	}
}
