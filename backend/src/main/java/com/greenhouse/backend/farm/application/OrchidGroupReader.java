package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrchidGroupReader {
	private final OrchidGroupRepository orchidGroupRepository;

	public OrchidGroupReader(OrchidGroupRepository orchidGroupRepository) {
		this.orchidGroupRepository = orchidGroupRepository;
	}

	public Optional<OrchidGroup> findById(Long orchidGroupId) {
		return orchidGroupRepository.findById(orchidGroupId);
	}
}
