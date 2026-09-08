package com.greenhouse.backend.common.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestActorProviderTest {

	@Test
	void normalizesTheRequestedWorkerWithoutSubstitutingAnAuthenticatedActor() {
		var provider = new RequestActorProvider(false, "demo");
		assertThat(provider.resolve("  작업자  ")).isEqualTo("작업자");
		assertThat(provider.resolve("  ")).isNull();
		assertThat(provider.resolve(null)).isNull();
	}

	@Test
	void demoOverridesBothMissingAndSuppliedWorkers() {
		var provider = new RequestActorProvider(true, "farm-demo");
		assertThat(provider.resolve("forged-worker")).isEqualTo("farm-demo");
		assertThat(provider.resolve(null)).isEqualTo("farm-demo");
	}

}
