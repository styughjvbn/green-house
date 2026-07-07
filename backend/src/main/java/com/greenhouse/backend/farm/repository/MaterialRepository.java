package com.greenhouse.backend.farm.repository;

import com.greenhouse.backend.farm.domain.Material;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaterialRepository extends JpaRepository<Material, Long> {

	@Query("""
		select material from Material material
		where (:keyword = ''
			or lower(material.code) like lower(concat('%', :keyword, '%'))
			or lower(material.name) like lower(concat('%', :keyword, '%')))
		  and (:category = '' or material.category = :category)
		  and (:manufacturer = '' or lower(coalesce(material.manufacturer, '')) like lower(concat('%', :manufacturer, '%')))
		  and (:active is null or material.active = :active)
		order by material.active desc, material.category, material.name
		""")
	List<Material> search(
		@Param("keyword") String keyword,
		@Param("category") String category,
		@Param("manufacturer") String manufacturer,
		@Param("active") Boolean active
	);

	Optional<Material> findTopByOrderByIdDesc();
}
