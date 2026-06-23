package com.greenhouse.backend.sales.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(name = "owner_name")
	private String ownerName;

	private String phone;

	@Column(columnDefinition = "text")
	private String address;

	@Column(columnDefinition = "text")
	private String memo;

	protected Customer() {
	}

	public Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}
}
