package com.greenhouse.backend.common.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RequestActorProvider {

	private final boolean demoMode;
	private final String demoUsername;

	public RequestActorProvider(
			@Value("${app.demo.enabled:false}") boolean demoMode,
			@Value("${app.demo.username:demo}") String demoUsername
	) {
		this.demoMode = demoMode;
		this.demoUsername = demoUsername;
	}

	public String resolve(String requestedActor) {
		if (demoMode) {
			return demoUsername;
		}
		if (requestedActor == null || requestedActor.isBlank()) {
			return null;
		}
		return requestedActor.trim();
	}
}
