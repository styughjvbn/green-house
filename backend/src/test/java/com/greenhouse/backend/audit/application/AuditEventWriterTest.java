package com.greenhouse.backend.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditEventWriterTest {

	private final AuditRecorder recorder = mock(AuditRecorder.class);

	private final AuditRequestContext context = mock(AuditRequestContext.class);

	private final AuditEventWriter writer = new AuditEventWriter(recorder, context);

	private final AuditEvent.Target target = new AuditEvent.Target("VARIETY", 1L, null, null, null, 1L);

	@Test
	void noChangesSkipContextAndPersistence() {
		assertThat(writer.record(AuditAction.UPDATED, AuditSource.VARIETY_MANAGEMENT, target, Map.of("name", "난"),
				Map.of("name", "난"), null))
			.isNull();
		verifyNoInteractions(recorder, context);
	}

	@Test
	void preservesFieldOrderNullValuesAndExplicitContext() {
		var identity = new AuditEvent.Identity("actor", "session", "client", "request");
		when(context.current()).thenReturn(identity);
		var before = new LinkedHashMap<String, Object>();
		before.put("name", "이전");
		before.put("description", null);
		var after = new LinkedHashMap<String, Object>();
		after.put("name", "현재");
		after.put("newField", "추가");
		writer.record(AuditAction.UPDATED, AuditSource.VARIETY_MANAGEMENT, target, before, after, null);
		var argument = ArgumentCaptor.forClass(AuditEvent.class);
		verify(recorder).record(argument.capture());
		var event = argument.getValue();
		assertThat(event.identity()).isEqualTo(identity);
		assertThat(event.target()).isEqualTo(target);
		assertThat(event.changedFields()).containsExactly("name", "newField");
		assertThat(event.beforeData()).isEqualTo(before);
		assertThat(event.contextData()).isEmpty();
	}

	@Test
	void cliIdentityDoesNotNeedHttpContextAndCopiesChangeMetadata() {
		var changes = new ArrayList<>(List.of("quantity"));
		var metadata = new LinkedHashMap<String, Object>();
		metadata.put("job", "import");
		metadata.put("optional", null);
		var event = new AuditEvent(new AuditEvent.Identity("cli-user", null, null, "job-1"), AuditAction.UPDATED,
				AuditSource.VARIETY_MANAGEMENT, target, changes, Map.of("quantity", 1), Map.of("quantity", 2),
				metadata);
		changes.clear();
		metadata.clear();
		assertThat(event.identity().sessionId()).isNull();
		assertThat(event.changedFields()).containsExactly("quantity");
		assertThat(event.contextData()).containsEntry("job", "import").containsKey("optional");
		verifyNoInteractions(context);
	}

}
