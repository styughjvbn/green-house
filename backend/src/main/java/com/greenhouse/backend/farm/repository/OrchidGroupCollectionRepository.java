package com.greenhouse.backend.farm.repository;

import com.greenhouse.backend.farm.domain.OrchidGroupCollection;
import com.greenhouse.backend.farm.domain.OrchidGroupCollectionStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrchidGroupCollectionRepository extends JpaRepository<OrchidGroupCollection, Long> {
	List<OrchidGroupCollection> findByStatusOrderByUpdatedAtDesc(OrchidGroupCollectionStatus status);

	List<OrchidGroupCollection> findAllByOrderByUpdatedAtDesc();
}
