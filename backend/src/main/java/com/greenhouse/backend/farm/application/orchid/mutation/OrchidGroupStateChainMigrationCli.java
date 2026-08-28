package com.greenhouse.backend.farm.application.orchid.mutation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.BackendApplication;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * ORCHID-CUTOVER: TRANSITION_ONLY — complete state-chain manifest를 검증·적재하는 operator CLI다.
 * Removal gate: 운영 cutover 완료 및 사후 복구 도구 보존 정책 확정.
 */
public final class OrchidGroupStateChainMigrationCli {

	private static final Set<String> OPERATOR_OPTIONS = Set.of(
			"cutover-key",
			"manifest",
			"effective-business-date",
			"minimum-writer-version",
			"apply",
			"confirmation");
	private static final Set<String> PROTECTED_OPTIONS = Set.of(
			"--spring.flyway.enabled",
			"--spring.jpa.hibernate.ddl-auto",
			"--spring.datasource.hikari.read-only",
			"--app.settlement.rebuild-on-startup",
			"--app.orchid-ledger.startup-guard-enabled");

	private OrchidGroupStateChainMigrationCli() {
	}

	public static void main(String[] args) {
		ParsedArguments parsed = parse(args);
		System.setProperty("spring.flyway.enabled", "false");
		System.setProperty("spring.jpa.hibernate.ddl-auto", "validate");
		System.setProperty("spring.datasource.hikari.read-only", "false");
		System.setProperty("app.settlement.rebuild-on-startup", "false");
		System.setProperty("app.orchid-ledger.startup-guard-enabled", "false");

		int exitCode;
		ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
		try {
			byte[] manifestBytes = Files.readAllBytes(parsed.manifestPath());
			OrchidGroupStateChainMigrationManifest manifest = objectMapper.readValue(
					manifestBytes, OrchidGroupStateChainMigrationManifest.class);
			try (ConfigurableApplicationContext context = new SpringApplicationBuilder(BackendApplication.class)
					.web(WebApplicationType.NONE)
					.run(parsed.springArguments().toArray(String[]::new))) {
				OrchidGroupStateChainMigrationService service = context
						.getBean(OrchidGroupStateChainMigrationService.class);
				OrchidGroupStateChainMigrationResult result = parsed.apply()
						? service.importManifest(
								parsed.cutoverKey(),
								parsed.effectiveBusinessDate(),
								parsed.minimumWriterVersion(),
								sha256(manifestBytes),
								manifest)
						: service.validate(
								parsed.cutoverKey(),
								parsed.effectiveBusinessDate(),
								parsed.minimumWriterVersion(),
								sha256(manifestBytes),
								manifest);
				printResult(objectMapper, result);
				exitCode = result.reconciliation().ready() ? 0 : 2;
			}
		} catch (IOException | RuntimeException exception) {
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
		boolean apply = parseBoolean(values.getOrDefault("apply", "false"));
		String expectedConfirmation = (apply ? "IMPORT:" : "PLAN:") + cutoverKey;
		if (!expectedConfirmation.equals(required(values, "confirmation"))) {
			throw new IllegalArgumentException("확인 문구가 일치하지 않습니다: " + expectedConfirmation);
		}
		return new ParsedArguments(
				cutoverKey,
				Path.of(required(values, "manifest")).toAbsolutePath().normalize(),
				LocalDate.parse(required(values, "effective-business-date")),
				required(values, "minimum-writer-version"),
				apply,
				List.copyOf(springArguments));
	}

	private static boolean parseBoolean(String value) {
		if ("true".equalsIgnoreCase(value)) {
			return true;
		}
		if ("false".equalsIgnoreCase(value)) {
			return false;
		}
		throw new IllegalArgumentException("--apply는 true 또는 false여야 합니다.");
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
					throw new IllegalArgumentException("State-chain migration 안전 옵션은 변경할 수 없습니다: "
							+ argument);
				});
	}

	private static String sha256(byte[] value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 fingerprint를 사용할 수 없습니다.", exception);
		}
	}

	private static void printResult(
			ObjectMapper objectMapper,
			OrchidGroupStateChainMigrationResult result) {
		try {
			System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("State-chain migration 결과를 출력할 수 없습니다.", exception);
		}
	}

	record ParsedArguments(
			UUID cutoverKey,
			Path manifestPath,
			LocalDate effectiveBusinessDate,
			String minimumWriterVersion,
			boolean apply,
			List<String> springArguments) {
	}
}
