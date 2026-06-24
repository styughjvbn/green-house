package com.greenhouse.backend.sales.dto;

import com.greenhouse.backend.sales.domain.Customer;

public record CustomerResponse(
	Long id,
	String name,
	String ownerName,
	String phone,
	String address,
	String memo
) {

	public static CustomerResponse from(Customer customer) {
		return new CustomerResponse(
			customer.getId(),
			customer.getName(),
			customer.getOwnerName(),
			customer.getPhone(),
			customer.getAddress(),
			customer.getMemo()
		);
	}
}
