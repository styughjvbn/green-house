package com.greenhouse.backend.farm.repository.variety;

import com.greenhouse.backend.farm.domain.variety.Variety;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface VarietyRepositoryCustom {

	Page<Variety> search(String keyword, String genus, Boolean saleEnabled, Boolean active, Pageable pageable);

	List<String> findDistinctGenera();
}
