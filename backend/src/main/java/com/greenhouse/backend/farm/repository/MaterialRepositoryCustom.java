package com.greenhouse.backend.farm.repository;

import com.greenhouse.backend.farm.domain.Material;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MaterialRepositoryCustom {

	Page<Material> search(String keyword, String category, String manufacturer, Boolean active, Pageable pageable);
}
