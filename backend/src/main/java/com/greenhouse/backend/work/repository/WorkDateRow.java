package com.greenhouse.backend.work.repository;

import java.time.LocalDate;

public interface WorkDateRow {
	Long getTargetId();

	LocalDate getLatestWorkDate();
}
