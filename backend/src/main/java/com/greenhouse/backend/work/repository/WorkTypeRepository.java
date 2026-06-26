package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.WorkType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkTypeRepository extends JpaRepository<WorkType, Long> {

	boolean existsByNameAndIdNot(String name, Long id);

	Optional<WorkType> findByCode(String code);

	Optional<WorkType> findByName(String name);

	List<WorkType> findAllByActiveTrueOrderBySortOrderAscIdAsc();

	List<WorkType> findAllByOrderBySortOrderAscIdAsc();
}
