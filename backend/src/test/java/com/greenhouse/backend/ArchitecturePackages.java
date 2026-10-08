package com.greenhouse.backend;

import java.util.Map;
import java.util.Set;

/** Ownership and roles for the legacy and feature-first layouts during ADR-004 migration. */
final class ArchitecturePackages {
  private static final String ROOT = "com.greenhouse.backend.";
  private static final Set<String> ROLES =
      Set.of(
          "application",
          "domain",
          "repository",
          "controller",
          "dto",
          "web",
          "api",
          "spi",
          "integration");
  private static final Map<String, Set<String>> FEATURES =
      Map.of(
          "farm",
              Set.of(
                  "structure",
                  "status",
                  "orchid",
                  "collection",
                  "inbound",
                  "variety",
                  "material",
                  "transformation",
                  "mutation"),
          "work", Set.of("operation", "target", "effect", "correction"),
          "sales", Set.of("document", "direct", "auction", "payment", "partner"));
  private static final Set<String> MUTATION_PARTS =
      Set.of("engine", "ledger", "query", "verification", "config");

  private ArchitecturePackages() {}

  static String module(String name) {
    String[] parts = parts(name);
    return parts.length == 0 ? "" : parts[0];
  }

  static String feature(String name) {
    String[] parts = parts(name);
    if (parts.length < 2) return "";
    Set<String> features = FEATURES.getOrDefault(parts[0], Set.of());
    if (features.contains(parts[1])) return parts[1];
    if (parts.length > 2
        && Set.of("application", "domain", "repository", "controller", "dto", "api", "spi")
            .contains(parts[1])
        && features.contains(parts[2])) return parts[2];
    return "";
  }

  static String role(String name) {
    String[] parts = parts(name);
    if (parts.length < 2) return "";
    if (ROLES.contains(parts[1])) return parts[1];
    if (!feature(name).isEmpty() && parts.length > 2) {
      if (ROLES.contains(parts[2])) return parts[2];
      if (parts[1].equals("mutation") && MUTATION_PARTS.contains(parts[2])) {
        if (parts[2].equals("ledger") && parts.length > 3 && ROLES.contains(parts[3]))
          return parts[3];
        return "application";
      }
    }
    return "";
  }

  static boolean isWeb(String name) {
    return Set.of("controller", "dto", "web").contains(role(name));
  }

  static boolean isHttpDto(String name) {
    return role(name).equals("dto") || (role(name).equals("web") && name.contains(".web.dto"));
  }

  static boolean isContract(String name) {
    return Set.of("application", "api", "spi").contains(role(name));
  }

  static boolean isExplicitContract(String name) {
    return Set.of("api", "spi").contains(role(name));
  }

  static boolean isModuleContract(String name) {
    String[] parts = parts(name);
    return parts.length > 1 && Set.of("api", "spi").contains(parts[1]);
  }

  static boolean isValidLayout(String name) {
    String[] parts = parts(name);
    if (parts.length < 2 || role(name).isEmpty()) return false;
    if (ROLES.contains(parts[1])) {
      // Keep support modules layered; migrated modules retain only known legacy feature paths.
      if (!FEATURES.containsKey(parts[0])
          || isModuleContract(name)
          || Set.of("web", "integration").contains(parts[1])) return true;
      if (Set.of("application", "repository", "controller").contains(parts[1]) && parts.length == 2)
        return true;
      return !feature(name).isEmpty();
    }
    if (feature(name).isEmpty()) return false;
    if (parts[1].equals("mutation")) return parts.length > 2 && MUTATION_PARTS.contains(parts[2]);
    return parts.length > 2 && ROLES.contains(parts[2]);
  }

  static String contractOwner(String name) {
    String module = module(name);
    String feature = feature(name);
    return module.equals("sales") && !feature.isEmpty() ? module + "." + feature : module;
  }

  private static String[] parts(String name) {
    return name.startsWith(ROOT) ? name.substring(ROOT.length()).split("\\.") : new String[0];
  }
}
