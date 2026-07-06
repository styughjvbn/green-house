package com.greenhouse.backend.partner.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "business_partners")
public class BusinessPartner extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "partner_type", nullable = false)
	private PartnerType partnerType;

	@Column(name = "owner_name")
	private String ownerName;

	private String phone;

	@Column(columnDefinition = "text")
	private String address;

	@Column(columnDefinition = "text")
	private String memo;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	protected BusinessPartner() {
	}

	public BusinessPartner(
		String name,
		PartnerType partnerType,
		String ownerName,
		String phone,
		String address,
		String memo
	) {
		this.name = name;
		this.partnerType = partnerType;
		this.ownerName = ownerName;
		this.phone = phone;
		this.address = address;
		this.memo = memo;
		this.active = true;
	}

	public Long getId() { return id; }
	public String getName() { return name; }
	public PartnerType getPartnerType() { return partnerType; }
	public String getOwnerName() { return ownerName; }
	public String getPhone() { return phone; }
	public String getAddress() { return address; }
	public String getMemo() { return memo; }
	public boolean isActive() { return active; }
}
