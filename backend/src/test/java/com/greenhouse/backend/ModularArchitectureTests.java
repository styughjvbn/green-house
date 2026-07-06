package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ModularArchitectureTests {
	private static final Path SOURCE_ROOT = Path.of("src/main/java/com/greenhouse/backend");
	private static final Set<String> MODULES = Set.of(
		"common", "farm", "work", "partner", "sales", "auction", "settlement", "dashboard", "print");
	private static final Set<String> STANDARD_LAYERS = Set.of(
		"domain", "repository", "application", "controller", "dto");
	private static final Map<String, Set<String>> ALLOWED_DEPENDENCIES = Map.of(
		"common", Set.of(),
		"farm", Set.of("common", "work"),
		"work", Set.of("common"),
		"partner", Set.of("common"),
		"sales", Set.of("common", "auction", "farm", "partner", "settlement"),
		"auction", Set.of("common", "partner"),
		"settlement", Set.of("common", "auction", "partner"),
		"dashboard", Set.of("common", "farm"),
		"print", Set.of("common", "sales"));
	private static final Pattern MODULE_IMPORT = Pattern.compile(
		"import com\\.greenhouse\\.backend\\.([a-z]+)\\.");

	@Test
	void moduleRootsAndLayersAreExplicit() throws IOException {
		for (String module : MODULES) {
			Path moduleRoot = SOURCE_ROOT.resolve(module);
			assertThat(moduleRoot).isDirectory();
			assertThat(moduleRoot.resolve("package-info.java")).exists();
			if (module.equals("common")) continue;

			try (Stream<Path> files = Files.walk(moduleRoot)) {
				List<Path> misplaced = files
					.filter(path -> path.toString().endsWith(".java"))
					.filter(path -> !path.getFileName().toString().equals("package-info.java"))
					.filter(path -> {
						Path relative = moduleRoot.relativize(path);
						return relative.getNameCount() < 2 || !STANDARD_LAYERS.contains(relative.getName(0).toString());
					})
					.toList();
				assertThat(misplaced).as("Files outside standard layers in %s", module).isEmpty();
			}
		}
	}

	@Test
	void modulesUseOnlyDeclaredDependencies() throws IOException {
		for (String module : MODULES) {
			for (Path source : javaSources(SOURCE_ROOT.resolve(module))) {
				var matcher = MODULE_IMPORT.matcher(Files.readString(source));
				while (matcher.find()) {
					String dependency = matcher.group(1);
					if (dependency.equals(module)) continue;
					assertThat(ALLOWED_DEPENDENCIES.get(module))
						.as("Undeclared dependency %s -> %s in %s", module, dependency, source)
						.contains(dependency);
				}
			}
		}
	}

	@Test
	void declaredModuleDependenciesAreAcyclic() {
		for (String module : MODULES) {
			assertThat(reaches(module, module, new java.util.HashSet<>()))
				.as("Cyclic module dependency starting at %s", module)
				.isFalse();
		}
	}

	@Test
	void layersDoNotReferenceForbiddenInnerLayers() throws IOException {
		assertNoImports("domain", ".dto.", ".repository.", ".application.", ".controller.");
		assertNoImports("repository", ".dto.", ".application.", ".controller.");
		assertNoImports("controller", ".repository.");
	}

	private void assertNoImports(String layer, String... forbiddenFragments) throws IOException {
		for (String module : MODULES) {
			Path layerRoot = SOURCE_ROOT.resolve(module).resolve(layer);
			if (!Files.isDirectory(layerRoot)) continue;
			for (Path source : javaSources(layerRoot)) {
				String content = Files.readString(source);
				for (String fragment : forbiddenFragments) {
					assertThat(content)
						.as("Forbidden %s dependency in %s", fragment, source)
						.doesNotContain(fragment);
				}
			}
		}
	}

	private List<Path> javaSources(Path root) throws IOException {
		try (Stream<Path> files = Files.walk(root)) {
			return files.filter(path -> path.toString().endsWith(".java")).toList();
		}
	}

	private boolean reaches(String current, String target, Set<String> visited) {
		if (!visited.add(current)) return false;
		for (String dependency : ALLOWED_DEPENDENCIES.get(current)) {
			if (dependency.equals(target) || reaches(dependency, target, visited)) return true;
		}
		return false;
	}
}
