package com.greenhouse.backend.common.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Preserve each API's existing choice between rejecting and clamping page input. */
public final class PageRequests {

	private static final int MAX_SIZE = 100;

	private PageRequests() {
	}

	public static void validate(int page, int size) {
		if (page < 0) {
			throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다.");
		}
		if (size < 1 || size > MAX_SIZE) {
			throw new IllegalArgumentException("페이지 크기는 1~100이어야 합니다.");
		}
	}

	public static PageRequest clamped(int page, int size) {
		return clamped(page, size, Sort.unsorted());
	}

	public static PageRequest clamped(int page, int size, Sort sort) {
		return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE), sort);
	}

}
