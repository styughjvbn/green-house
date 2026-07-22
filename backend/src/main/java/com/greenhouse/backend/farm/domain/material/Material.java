package com.greenhouse.backend.farm.domain.material;

import com.greenhouse.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "materials")
public class Material extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 50)
	private String code;

	@Column(nullable = false, length = 50)
	private String category;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(length = 150)
	private String manufacturer;

	@Column(length = 150)
	private String specification;

	@Column(name = "stock_quantity", length = 50)
	private String stockQuantity;

	@Column(name = "storage_location", length = 150)
	private String storageLocation;

	@Column(columnDefinition = "text")
	private String usage;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	public Material(
			String code,
			String category,
			String name,
			String manufacturer,
			String specification,
			String stockQuantity,
			String storageLocation,
			String usage,
			boolean active) {
		this.code = code;
		this.category = category;
		this.name = name;
		this.manufacturer = manufacturer;
		this.specification = specification;
		this.stockQuantity = stockQuantity;
		this.storageLocation = storageLocation;
		this.usage = usage;
		this.active = active;
	}

	public void update(
			String category,
			String name,
			String manufacturer,
			String specification,
			String stockQuantity,
			String storageLocation,
			String usage) {
		this.category = category;
		this.name = name;
		this.manufacturer = manufacturer;
		this.specification = specification;
		this.stockQuantity = stockQuantity;
		this.storageLocation = storageLocation;
		this.usage = usage;
	}

	public void deactivate() {
		this.active = false;
	}
}
