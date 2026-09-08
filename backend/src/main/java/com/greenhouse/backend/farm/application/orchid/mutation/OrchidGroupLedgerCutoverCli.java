package com.greenhouse.backend.farm.application.orchid.mutation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.BackendApplication;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * ORCHID-CUTOVER: RECOVERY — complete state-chain 검증과 ACTIVE 전환을 실행하는 operator CLI다.
 * Removal gate: V20 백업 복구 절차를 대체하는 도구가 검증될 때까지 보존.
 */
public final class OrchidGroupLedgerCutoverCli {

	private static final Set<String> OPERATOR_OPTIONS = Set.of("cutover-key", "effective-business-date",
			"minimum-writer-version", "current-writer-version", "activate", "confirmation");

	private static final Set<String> PROTECTED_OPTIONS = Set.of("--spring.flyway.enabled",
			"--spring.jpa.hibernate.ddl-auto", "--spring.datasource.hikari.read-only",
			"--app.settlement.rebuild-on-startup", "--app.orchid-ledger.startup-guard-enabled");

	private OrchidGroupLedgerCutoverCli() {
	}

	public static void main(String[] args) {
		ParsedArguments parsed = parse(args);
		System.setProperty("spring.flyway.enabled", "false");
		System.setProperty("spring.jpa.hibernate.ddl-auto", "validate");
		System.setProperty("spring.datasource.hikari.read-only", "false");
		System.setProperty("app.settlement.rebuild-on-startup", "false");
		System.setProperty("app.orchid-ledger.startup-guard-enabled", "false");

		int exitCode;
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(BackendApplication.class)
			.web(WebApplicationType.NONE)
			.run(parsed.springArguments().toArray(String[]::new))) {
			OrchidGroupLedgerCutoverResult result = context.getBean(OrchidGroupLedgerCutoverService.class)
				.execute(parsed.command());
			printResult(new ObjectMapper().findAndRegisterModules(), result);
			exitCode = result.reconciliation().ready() ? 0 : 2;
		}
		catch (RuntimeException exception) {
			exception.printStackTrace(System.err);
			exitCode = 1;
		}
		System.exit(exitCode);
	}

	static ParsedArguments parse(String[] args) {
		rejectUnsafeOverrides(args);
		Map<String, String> values = new LinkedHashMap<>();
		List<String> springArguments = new ArrayList<>();
		for (String argument : args) {
			if (!argument.startsWith("--") || !argument.contains("=")) {
				springArguments.add(argument);
				continue;
			}
			int delimiter = argument.indexOf('=');
			String name = argument.substring(2, delimiter);
			if (!OPERATOR_OPTIONS.contains(name)) {
				springArguments.add(argument);
				continue;
			}
			String previous = values.putIfAbsent(name, argument.substring(delimiter + 1));
			if (previous != null) {
				throw new IllegalArgumentException("Operator 옵션을 중복 지정할 수 없습니다: --" + name);
			}
		}

		UUID cutoverKey = UUID.fromString(required(values, "cutover-key"));
		LocalDate effectiveBusinessDate = LocalDate.parse(required(values, "effective-business-date"));
		boolean activate = parseBoolean(values.getOrDefault("activate", "false"));
		String confirmation = required(values, "confirmation");
		String expectedConfirmation = (activate ? "ACTIVATE:" : "VERIFY:") + cutoverKey;
		if (!expectedConfirmation.equals(confirmation)) {
			throw new IllegalArgumentException("확인 문구가 일치하지 않습니다: " + expectedConfirmation);
		}
		OrchidGroupLedgerCutoverCommand command = new OrchidGroupLedgerCutoverCommand(cutoverKey, effectiveBusinessDate,
				required(values, "minimum-writer-version"), required(values, "current-writer-version"), activate);
		return new ParsedArguments(command, List.copyOf(springArguments));
	}

	private static boolean parseBoolean(String value) {
		if ("true".equalsIgnoreCase(value)) {
			return true;
		}
		if ("false".equalsIgnoreCase(value)) {
			return false;
		}
		throw new IllegalArgumentException("--activate는 true 또는 false여야 합니다.");
	}

	private static String required(Map<String, String> values, String name) {
		String value = values.get(name);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("필수 Operator 옵션이 없습니다: --" + name);
		}
		return value.trim();
	}

	private static void rejectUnsafeOverrides(String[] args) {
		Arrays.stream(args)
			.filter(argument -> PROTECTED_OPTIONS.stream()
				.anyMatch(option -> argument.equals(option) || argument.startsWith(option + "=")))
			.forEach(argument -> {
				throw new IllegalArgumentException("Cutover 안전 옵션은 변경할 수 없습니다: " + argument);
			});
	}

	private static void printResult(ObjectMapper objectMapper, OrchidGroupLedgerCutoverResult result) {
		try {
			System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("Cutover 결과를 JSON으로 출력할 수 없습니다.", exception);
		}
	}

	record ParsedArguments(OrchidGroupLedgerCutoverCommand command, List<String> springArguments) {
	}

}
