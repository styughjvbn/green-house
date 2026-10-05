package com.greenhouse.backend.support;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Discovers bytecode field writes, including methods that delegate to private mutators. */
public final class EntityWriterInspection {
  private EntityWriterInspection() {}

  public static Set<JavaMethod> stateMethods(JavaClass entity) {
    Set<JavaMethod> writers =
        entity.getMethods().stream()
            .filter(
                method ->
                    method.getAccessesFromSelf().stream()
                        .filter(JavaFieldAccess.class::isInstance)
                        .map(JavaFieldAccess.class::cast)
                        .anyMatch(
                            access ->
                                access.getAccessType() == JavaFieldAccess.AccessType.SET
                                    && access.getTargetOwner().equals(entity)))
            .collect(Collectors.toCollection(HashSet::new));
    boolean changed;
    do {
      changed = false;
      for (JavaMethod method : entity.getMethods()) {
        boolean delegates =
            Stream.concat(
                    method.getMethodCallsFromSelf().stream()
                        .flatMap(call -> call.getTarget().resolveMember().stream()),
                    method.getMethodReferencesFromSelf().stream()
                        .flatMap(reference -> reference.getTarget().resolveMember().stream()))
                .anyMatch(writers::contains);
        if (delegates) changed |= writers.add(method);
      }
    } while (changed);
    return Set.copyOf(writers);
  }

  public static Set<String> stateCallers(JavaClasses classes, Class<?> entityType) {
    var entity = classes.get(entityType);
    Set<String> methods =
        stateMethods(entity).stream().map(JavaMethod::getFullName).collect(Collectors.toSet());
    return classes.stream()
        .filter(type -> !type.equals(entity))
        .flatMap(
            type ->
                Stream.concat(
                    type.getFieldAccessesFromSelf().stream()
                        .filter(
                            access ->
                                access.getAccessType() == JavaFieldAccess.AccessType.SET
                                    && access.getTargetOwner().equals(entity))
                        .map(access -> access.getOriginOwner().getName()),
                    Stream.concat(
                        type.getMethodCallsFromSelf().stream()
                            .filter(call -> methods.contains(call.getTarget().getFullName()))
                            .map(call -> call.getOriginOwner().getName()),
                        type.getMethodReferencesFromSelf().stream()
                            .filter(
                                reference -> methods.contains(reference.getTarget().getFullName()))
                            .map(reference -> reference.getOriginOwner().getName()))))
        .collect(Collectors.toSet());
  }
}
