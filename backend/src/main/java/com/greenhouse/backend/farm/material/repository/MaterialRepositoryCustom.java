package com.greenhouse.backend.farm.material.repository;

import com.greenhouse.backend.farm.material.domain.Material;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MaterialRepositoryCustom {

  Page<Material> search(
      String keyword, String category, String manufacturer, Boolean active, Pageable pageable);
}
