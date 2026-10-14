package com.greenhouse.backend;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A root-name guard, not a SQL/JPQL parser. Dynamic queries and association aliases need review.
 */
final class AnnotatedQueryTargets {
  private static final String IDENTIFIER = "(?:\"[a-zA-Z_][\\w$]*\"|[a-zA-Z_][\\w$]*)";
  private static final Pattern ROOT =
      Pattern.compile(
          "(?i)\\b(?:from|join|update|into)\\s+("
              + IDENTIFIER
              + "(?:\\s*\\.\\s*"
              + IDENTIFIER
              + ")*)");

  private AnnotatedQueryTargets() {}

  static Set<String> names(String query, boolean nativeQuery) {
    Set<String> names = new LinkedHashSet<>();
    var matcher = ROOT.matcher(query);
    while (matcher.find()) {
      String qualified = matcher.group(1).replaceAll("\\s+", "");
      String name = qualified.substring(qualified.lastIndexOf('.') + 1);
      boolean quoted = name.startsWith("\"");
      if (quoted) name = name.substring(1, name.length() - 1);
      names.add(nativeQuery && !quoted ? name.toLowerCase(Locale.ROOT) : name);
    }
    return names;
  }
}
