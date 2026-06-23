package com.greenhouse.backend.farm.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "houses")
public class House extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private Integer number;

	@Column(nullable = false)
	private String name;

	@Column(columnDefinition = "text")
	private String memo;

	@OneToMany(mappedBy = "house", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("displayOrder ASC")
	private List<PhysicalBed> physicalBeds = new ArrayList<>();

	protected House() {
	}

	public House(Integer number, String name) {
		this.number = number;
		this.name = name;
	}

	public void addPhysicalBed(PhysicalBed physicalBed) {
		this.physicalBeds.add(physicalBed);
		physicalBed.setHouse(this);
	}

	public Long getId() {
		return id;
	}

	public Integer getNumber() {
		return number;
	}

	public String getName() {
		return name;
	}

	public String getMemo() {
		return memo;
	}

	public List<PhysicalBed> getPhysicalBeds() {
		return physicalBeds;
	}
}
