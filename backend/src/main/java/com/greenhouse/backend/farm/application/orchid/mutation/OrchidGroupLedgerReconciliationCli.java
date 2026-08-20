package com.greenhouse.backend.farm.application.orchid.mutation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.BackendApplication;
import java.util.Arrays;
import java.util.Set;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class OrchidGroupLedgerReconciliationCli {

	private static final Set<String> PROTECTED_OPTIONS = Set.of(
			"--spring.flyway.enabled",
			"--spring.jpa.hibernate.ddl-auto",
			"--spring.datasource.hikari.read-only",
			"--app.settlement.rebuild-on-startup",
			"--app.orchid-ledger.startup-guard-enabled");

	private OrchidGroupLedgerReconciliationCli() {
	}

	public static void main(String[] args) {
		rejectUnsafeOverrides(args);
		System.setProperty("spring.flyway.enabled", "false");
		System.setProperty("spring.jpa.hibernate.ddl-auto", "validate");
		System.setProperty("spring.datasource.hikari.read-only", "true");
		System.setProperty("app.settlement.rebuild-on-startup", "false");
		System.setProperty("app.orchid-ledger.startup-guard-enabled", "false");

		int exitCode;
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(BackendApplication.class)
				.web(WebApplicationType.NONE)
				.run(args)) {
			OrchidGroupLedgerReconciliationReport report = context
					.getBean(OrchidGroupLedgerReconciliationService.class)
					.reconcile();
			printReport(context.getBean(ObjectMapper.class), report);
			exitCode = report.ready() ? 0 : 2;
		} catch (RuntimeException exception) {
			exception.printStackTrace(System.err);
			exitCode = 1;
		}
		System.exit(exitCode);
	}

	private static void rejectUnsafeOverrides(String[] args) {
		Arrays.stream(args)
				.filter(argument -> PROTECTED_OPTIONS.stream()
						.anyMatch(option -> argument.equals(option) || argument.startsWith(option + "=")))
				.forEach(argument -> {
					throw new IllegalArgumentException("읽기 전용 옵션은 변경할 수 없습니다: " + argument);
				});
	}

	private static void printReport(
			ObjectMapper objectMapper,
			OrchidGroupLedgerReconciliationReport report) {
		try {
			System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("대사 보고서를 JSON으로 출력할 수 없습니다.", exception);
		}
	}
}
