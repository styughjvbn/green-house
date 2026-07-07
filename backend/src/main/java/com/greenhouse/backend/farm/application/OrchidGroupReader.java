package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;

import lombok.RequiredArgsConstructor;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrchidGroupReader {
	private final OrchidGroupRepository orchidGroupRepository;

	public Optional<OrchidGroup> findById(Long orchidGroupId) {
		return orchidGroupRepository.findById(orchidGroupId);
	}
}
