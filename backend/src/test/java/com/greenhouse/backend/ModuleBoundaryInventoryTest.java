package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Exact, temporary exceptions while module APIs replace persistence and HTTP coupling.
 */
class ModuleBoundaryInventoryTest {

	private static final String BASE_PACKAGE = "com.greenhouse.backend.";

	private static final JavaClasses CLASSES = new ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackages(BASE_PACKAGE);

	private static final Path INVENTORY_ROOT = Path.of("src/test/resources/architecture");

	private static final Path REPORT_ROOT = Path.of("build/reports/architecture");

	private static final Pattern QUERY_TARGET = Pattern
		.compile("(?i)\\b(?:from|join|update|into)\\s+([a-zA-Z_][\\w.]*)");

	@Test
	void compiledDependenciesFollowTheDeclaredModuleGraph() {
		for (JavaClass origin : CLASSES) {
			if (module(origin).isEmpty())
				continue;
			assertThat(ModularArchitectureTests.ALLOWED_DEPENDENCIES).containsKey(module(origin));
			for (var dependency : origin.getDirectDependenciesFromSelf()) {
				JavaClass target = dependency.getTargetClass();
				if (!isCrossModule(origin, target))
					continue;
				assertThat(ModularArchitectureTests.ALLOWED_DEPENDENCIES.get(module(origin)))
					.as("Undeclared compiled dependency %s -> %s", origin.getName(), target.getName())
					.contains(module(target));
			}
		}
	}

	@Test
	void crossModuleImplementationDependenciesDoNotGrow() throws IOException {
		Set<String> dependencies = new TreeSet<>();
		for (JavaClass origin : CLASSES) {
			if (isQueryType(origin))
				continue;
			for (var dependency : origin.getDirectDependenciesFromSelf()) {
				JavaClass target = dependency.getTargetClass();
				if (!isCrossModule(origin, target))
					continue;
				String kind = implementationKind(target);
				if (kind != null)
					dependencies.add(kind + "\t" + origin.getName() + "\t" + target.getName());
			}
		}
		assertInventory("module-implementation-dependencies.tsv", dependencies);
	}

	@Test
	void uncontrolledTimeReadsDoNotGrow() throws IOException {
		Set<String> dependencies = new TreeSet<>();
		for (JavaClass origin : CLASSES) {
			for (var call : origin.getMethodCallsFromSelf()) {
				var target = call.getTarget();
				String owner = target.getOwner().getName();
				boolean readsSystemTime = owner.startsWith("java.time.") && target.getName().equals("now")
						&& target.getRawParameterTypes()
							.stream()
							.noneMatch(type -> type.isEquivalentTo(java.time.Clock.class));
				boolean constructsSystemClock = owner.equals("java.time.Clock") && target.getName().startsWith("system")
						&& !origin.getName().equals(BASE_PACKAGE + "common.config.TimeConfig");
				if (readsSystemTime || constructsSystemClock
						|| (owner.equals("java.lang.System") && target.getName().equals("currentTimeMillis"))) {
					dependencies.add(origin.getName() + "\t" + target.getFullName());
				}
			}
		}
		assertInventory("uncontrolled-time-calls.tsv", dependencies);
	}

	@Test
	void annotatedQueriesDoNotReadForeignEntitiesOrTables() throws IOException {
		Map<String, JavaClass> entities = CLASSES.stream()
			.filter(type -> type.isAnnotatedWith("jakarta.persistence.Entity"))
			.collect(Collectors.toMap(JavaClass::getSimpleName, Function.identity()));
		Map<String, JavaClass> tables = entities.values()
			.stream()
			.filter(type -> type.isAnnotatedWith("jakarta.persistence.Table"))
			.collect(Collectors.toMap(
					type -> type.getAnnotationOfType("jakarta.persistence.Table").get("name").orElseThrow().toString(),
					Function.identity()));
		Set<String> dependencies = new TreeSet<>();
		for (JavaClass origin : CLASSES) {
			for (var method : origin.getMethods()) {
				if (!method.isAnnotatedWith("org.springframework.data.jpa.repository.Query"))
					continue;
				var annotation = method.getAnnotationOfType("org.springframework.data.jpa.repository.Query");
				boolean nativeQuery = Boolean.TRUE.equals(annotation.get("nativeQuery").orElse(false));
				for (String attribute : Set.of("value", "countQuery")) {
					String query = annotation.get(attribute).orElse("").toString();
					var matcher = QUERY_TARGET.matcher(query);
					while (matcher.find()) {
						String name = matcher.group(1);
						JavaClass target = (nativeQuery ? tables : entities).get(name);
						if (target != null && isCrossModule(origin, target)) {
							dependencies.add((nativeQuery ? "SQL" : "JPQL") + "\t" + method.getFullName() + "\t"
									+ target.getName());
						}
					}
				}
			}
		}
		assertInventory("module-query-dependencies.tsv", dependencies);
	}

	private String implementationKind(JavaClass target) {
		if (target.isAnnotatedWith("jakarta.persistence.Entity"))
			return "ENTITY";
		if (isQueryType(target))
			return "QUERY_TYPE";
		if (target.getPackageName().contains(".repository"))
			return "REPOSITORY";
		if (target.getPackageName().contains(".dto"))
			return "HTTP_DTO";
		if (target.getPackageName().contains(".controller"))
			return "CONTROLLER";
		return null;
	}

	private static boolean isQueryType(JavaClass type) {
		return type.getSimpleName().startsWith("Q") && type.isAssignableTo("com.querydsl.core.types.EntityPath");
	}

	private boolean isCrossModule(JavaClass origin, JavaClass target) {
		String targetModule = module(target);
		return !targetModule.isEmpty() && !module(origin).equals(targetModule);
	}

	private String module(JavaClass type) {
		String name = type.getPackageName();
		if (!name.startsWith(BASE_PACKAGE))
			return "";
		return name.substring(BASE_PACKAGE.length()).split("\\.")[0];
	}

	private void assertInventory(String filename, Set<String> actual) throws IOException {
		Files.createDirectories(REPORT_ROOT);
		Files.write(REPORT_ROOT.resolve(filename), actual);
		Path baseline = INVENTORY_ROOT.resolve(filename);
		assertThat(baseline).as("Reviewed exception inventory; current report: %s", REPORT_ROOT.resolve(filename))
			.exists();
		Set<String> expected = Files.readAllLines(baseline)
			.stream()
			.filter(line -> !line.isBlank() && !line.startsWith("#"))
			.collect(Collectors.toCollection(TreeSet::new));
		assertThat(actual)
			.as("No new module bypasses; remove obsolete exceptions as contracts are migrated (%s)", filename)
			.containsExactlyInAnyOrderElementsOf(expected);
	}

}
