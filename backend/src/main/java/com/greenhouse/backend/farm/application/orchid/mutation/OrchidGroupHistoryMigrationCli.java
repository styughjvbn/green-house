package com.greenhouse.backend.farm.application.orchid.mutation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.BackendApplication;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
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

public final class OrchidGroupHistoryMigrationCli {

	private static final Set<String> OPERATOR_OPTIONS = Set.of(
			"run-key",
			"source-cutoff",
			"backup-fingerprint",
			"manifest",
			"effective-business-date",
			"apply",
			"confirmation");
	private static final Set<String> PROTECTED_OPTIONS = Set.of(
			"--spring.flyway.enabled",
			"--spring.jpa.hibernate.ddl-auto",
			"--spring.datasource.hikari.read-only",
			"--app.settlement.rebuild-on-startup",
			"--app.orchid-ledger.startup-guard-enabled");

	private OrchidGroupHistoryMigrationCli() {
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
			OrchidGroupHistoryMigrationManifest manifest = objectMapper.readValue(
					manifestBytes, OrchidGroupHistoryMigrationManifest.class);
			OrchidGroupHistoryMigrationOperatorCommand command = parsed.command(sha256(manifestBytes));
			try (ConfigurableApplicationContext context = new SpringApplicationBuilder(BackendApplication.class)
					.web(WebApplicationType.NONE)
					.run(parsed.springArguments().toArray(String[]::new))) {
				OrchidGroupHistoryMigrationOperatorResult result = context
						.getBean(OrchidGroupHistoryMigrationOperatorService.class)
						.execute(command, manifest);
				printResult(objectMapper, result);
				exitCode = !command.apply() || Boolean.TRUE.equals(result.verification().get("ready")) ? 0 : 2;
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

		UUID runKey = UUID.fromString(required(values, "run-key"));
		boolean apply = parseBoolean(values.getOrDefault("apply", "false"));
		String expectedConfirmation = (apply ? "IMPORT:" : "PLAN:") + runKey;
		if (!expectedConfirmation.equals(required(values, "confirmation"))) {
			throw new IllegalArgumentException("확인 문구가 일치하지 않습니다: " + expectedConfirmation);
		}
		String backupFingerprint = required(values, "backup-fingerprint");
		if (!backupFingerprint.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("백업 fingerprint 형식이 올바르지 않습니다.");
		}
		return new ParsedArguments(
				runKey,
				Instant.parse(required(values, "source-cutoff")),
				backupFingerprint,
				Path.of(required(values, "manifest")).toAbsolutePath().normalize(),
				LocalDate.parse(required(values, "effective-business-date")),
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
					throw new IllegalArgumentException("Historical migration 안전 옵션은 변경할 수 없습니다: "
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
			OrchidGroupHistoryMigrationOperatorResult result) {
		try {
			System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Historical migration 결과를 JSON으로 출력할 수 없습니다.", exception);
		}
	}

	record ParsedArguments(
			UUID runKey,
			Instant sourceCutoff,
			String backupFingerprint,
			Path manifestPath,
			LocalDate effectiveBusinessDate,
			boolean apply,
			List<String> springArguments) {

		OrchidGroupHistoryMigrationOperatorCommand command(String manifestFingerprint) {
			return new OrchidGroupHistoryMigrationOperatorCommand(
					runKey,
					sourceCutoff,
					backupFingerprint,
					manifestFingerprint,
					effectiveBusinessDate,
					apply);
		}
	}
}
