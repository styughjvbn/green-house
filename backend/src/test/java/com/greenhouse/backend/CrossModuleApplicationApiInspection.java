package com.greenhouse.backend;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import java.util.Set;
import java.util.TreeSet;

final class CrossModuleApplicationApiInspection {
  private static final String ROOT = "com.greenhouse.backend.";

  private CrossModuleApplicationApiInspection() {}

  static Set<String> contracts(JavaClasses classes) {
    Set<String> result = new TreeSet<>();
    for (JavaClass origin : classes) {
      for (var dependency : origin.getDirectDependenciesFromSelf()) {
        JavaClass target = dependency.getTargetClass();
        if (crossModuleApplication(origin, target)) result.add("TYPE\t" + target.getName());
      }
      for (var call : origin.getMethodCallsFromSelf()) {
        if (crossModuleApplication(origin, call.getTargetOwner()))
          result.add("METHOD\t" + call.getTarget().getFullName());
      }
      for (var reference : origin.getMethodReferencesFromSelf()) {
        if (crossModuleApplication(origin, reference.getTargetOwner()))
          result.add("METHOD\t" + reference.getTarget().getFullName());
      }
      for (var call : origin.getConstructorCallsFromSelf()) {
        if (crossModuleApplication(origin, call.getTargetOwner()))
          result.add("CONSTRUCTOR\t" + call.getTarget().getFullName());
      }
      for (var reference : origin.getConstructorReferencesFromSelf()) {
        if (crossModuleApplication(origin, reference.getTargetOwner()))
          result.add("CONSTRUCTOR\t" + reference.getTarget().getFullName());
      }
    }
    return result;
  }

  private static boolean crossModuleApplication(JavaClass origin, JavaClass target) {
    return !module(origin).isEmpty()
        && !module(target).isEmpty()
        && !module(origin).equals(module(target))
        && (target.getPackageName().endsWith(".application")
            || target.getPackageName().contains(".application."));
  }

  private static String module(JavaClass type) {
    if (!type.getPackageName().startsWith(ROOT)) return "";
    return type.getPackageName().substring(ROOT.length()).split("\\.")[0];
  }
}
