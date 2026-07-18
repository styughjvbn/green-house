package com.greenhouse.backend.farm.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "varieties", uniqueConstraints = @UniqueConstraint(name = "uk_varieties_genus_name", columnNames = {
		"genus", "name" }))
public class Variety extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 50)
	private String code;

	@Column(nullable = false, length = 100)
	private String genus;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(length = 150)
	private String alias;

	@Column(name = "default_pot_size", length = 50)
	private String defaultPotSize;

	@Column(name = "sale_enabled", nullable = false)
	private boolean saleEnabled;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@Column(columnDefinition = "text")
	private String description;

	@Column(columnDefinition = "text")
	private String memo;

	public Variety(
			String code,
			String genus,
			String name,
			String alias,
			String defaultPotSize,
			boolean saleEnabled,
			boolean active,
			String description,
			String memo) {
		this.code = code;
		this.genus = genus;
		this.name = name;
		this.alias = alias;
		this.defaultPotSize = PotSizeCode.fromInput(defaultPotSize).getDisplayValue();
		this.saleEnabled = saleEnabled;
		this.active = active;
		this.description = description;
		this.memo = memo;
	}

	public void update(
			String genus,
			String name,
			String alias,
			String defaultPotSize,
			boolean saleEnabled,
			String description,
			String memo) {
		this.genus = genus;
		this.name = name;
		this.alias = alias;
		this.defaultPotSize = PotSizeCode.fromInput(defaultPotSize).getDisplayValue();
		this.saleEnabled = saleEnabled;
		this.description = description;
		this.memo = memo;
	}

	public void deactivate() {
		this.active = false;
	}
}
