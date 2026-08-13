package com.greenhouse.backend.sales.repository;

import java.sql.Date;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SalesSlipNumberRepository {

	private final JdbcTemplate jdbcTemplate;
	private volatile Boolean h2Database;

	public long nextDailySequence(LocalDate saleDate) {
		if (isH2Database()) {
			return nextH2Sequence(saleDate);
		}
		Long nextValue = jdbcTemplate.queryForObject("""
				insert into sales_slip_daily_sequences (sale_date, last_value)
				values (?, 1)
				on conflict (sale_date)
				do update set last_value = sales_slip_daily_sequences.last_value + 1
				returning last_value
				""", Long.class, Date.valueOf(saleDate));
		if (nextValue == null) {
			throw new IllegalStateException("판매 전표 일련번호를 생성할 수 없습니다.");
		}
		return nextValue;
	}

	private long nextH2Sequence(LocalDate saleDate) {
		Date date = Date.valueOf(saleDate);
		jdbcTemplate.update("""
				merge into sales_slip_daily_sequences (sale_date, last_value)
				key (sale_date)
				values (?, coalesce((
					select current_sequence.last_value + 1
					from sales_slip_daily_sequences current_sequence
					where current_sequence.sale_date = ?
				), 1))
				""", date, date);
		Long nextValue = jdbcTemplate.queryForObject(
				"select last_value from sales_slip_daily_sequences where sale_date = ?",
				Long.class,
				date);
		if (nextValue == null) {
			throw new IllegalStateException("판매 전표 일련번호를 생성할 수 없습니다.");
		}
		return nextValue;
	}

	private boolean isH2Database() {
		Boolean cached = h2Database;
		if (cached != null) {
			return cached;
		}
		Boolean detected = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection ->
				connection.getMetaData().getDatabaseProductName().startsWith("H2"));
		h2Database = Boolean.TRUE.equals(detected);
		return h2Database;
	}
}
