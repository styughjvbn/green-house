package com.greenhouse.backend.farm.repository.material;

import com.greenhouse.backend.farm.domain.material.Material;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MaterialRepositoryCustom {

	Page<Material> search(String keyword, String category, String manufacturer, Boolean active, Pageable pageable);
}
