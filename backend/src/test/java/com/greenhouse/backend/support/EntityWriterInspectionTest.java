package com.greenhouse.backend.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.Test;

class EntityWriterInspectionTest {
  @Test
  void findsNewNamedMutatorsAndDelegatingMethodsWithoutAnInventory() {
    var classes = new ClassFileImporter().importClasses(State.class, RogueWriter.class);
    assertThat(EntityWriterInspection.stateMethods(classes.get(State.class)))
        .extracting(JavaMethod::getName)
        .containsExactlyInAnyOrder("newlyAddedMutator", "delegate", "privateAssignment");
    assertThat(EntityWriterInspection.stateCallers(classes, State.class))
        .containsExactly(RogueWriter.class.getName());
  }

  @Test
  void methodReferencesAreAlsoDetectedAsStateWriterDependencies() {
    var classes = new ClassFileImporter().importClasses(State.class, ReferenceWriter.class);
    assertThat(EntityWriterInspection.stateCallers(classes, State.class))
        .containsExactly(ReferenceWriter.class.getName());
  }

  @Test
  void aDirectFieldWriteCannotBypassTheMethodGate() {
    var classes = new ClassFileImporter().importClasses(State.class, DirectFieldWriter.class);
    assertThat(EntityWriterInspection.stateCallers(classes, State.class))
        .containsExactly(DirectFieldWriter.class.getName());
  }

  @Test
  void readersAndConstructorsDoNotCountAsExistingEntityMutators() {
    var classes = new ClassFileImporter().importClasses(State.class, Reader.class);
    assertThat(EntityWriterInspection.stateCallers(classes, State.class)).isEmpty();
  }

  static class State {
    private int value;

    State() {
      value = 1;
    }

    int read() {
      return value;
    }

    void newlyAddedMutator(int next) {
      value = next;
    }

    void delegate(int next) {
      privateAssignment(next);
    }

    private void privateAssignment(int next) {
      value = next;
    }
  }

  static class RogueWriter {
    void write(State state) {
      state.newlyAddedMutator(2);
      state.delegate(3);
    }
  }

  static class ReferenceWriter {
    IntConsumer write(State state) {
      return state::newlyAddedMutator;
    }
  }

  static class Reader {
    int read() {
      return new State().read();
    }
  }

  static class DirectFieldWriter {
    void write(State state) {
      state.value = 4;
    }
  }
}
