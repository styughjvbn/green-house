plugins {
	java
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.diffplug.spotless") version "8.10.2"
}

group = "com.greenhouse"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("com.querydsl:querydsl-jpa:5.1.0:jakarta")
	// OpenAPI + Swagger UI
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
	annotationProcessor("com.querydsl:querydsl-apt:5.1.0:jakarta")
	annotationProcessor("jakarta.annotation:jakarta.annotation-api")
	annotationProcessor("jakarta.persistence:jakarta.persistence-api")

	runtimeOnly("org.postgresql:postgresql")
	
	testRuntimeOnly("com.h2database:h2")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("com.tngtech.archunit:archunit:1.4.2")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	// Lombok
	testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}

spotless {
	java {
		shortenFullyQualifiedTypes()
		importOrder()
		removeUnusedImports()
		googleJavaFormat()
	}
}


tasks.withType<Test> {
	useJUnitPlatform()
	// Full Spring/ArchUnit suites exceed Gradle's default 512 MiB test worker heap.
	maxHeapSize = providers.gradleProperty("backendTestHeap").orElse("2g").get()
	// Report OOM immediately instead of leaving the worker's remote shutdown waiting.
	jvmArgs("-XX:+ExitOnOutOfMemoryError")
}

tasks.named<Test>("test") {
	useJUnitPlatform {
		excludeTags("work-e2e", "work-benchmark", "domain-benchmark", "domain-diagnosis")
	}
}


tasks.register("format") {
	group = "formatting"
	description = "Formats Java source code and cleans imports"
	dependsOn("spotlessApply")
}

tasks.register<JavaExec>("openApiRun") {
	group = "documentation"
	description = "Runs the backend with the test-profile H2 database for OpenAPI generation."
	dependsOn(tasks.named("testClasses"))
	classpath = sourceSets["test"].runtimeClasspath
	mainClass.set("com.greenhouse.backend.BackendApplication")
	args("--spring.profiles.active=test")
}

tasks.register<JavaExec>("orchidLedgerReconcile") {
	group = "verification"
	description = "Runs the read-only OrchidGroup ledger rehearsal checks against a restored database."
	dependsOn(tasks.named("classes"))
	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set("com.greenhouse.backend.farm.mutation.verification.OrchidGroupLedgerReconciliationCli")
}

tasks.register<JavaExec>("orchidLedgerStartupVerify") {
	group = "verification"
	description = "Runs Hibernate validation and the OrchidGroup ledger startup guard without HTTP."
	dependsOn(tasks.named("classes"))
	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set("com.greenhouse.backend.farm.mutation.verification.OrchidGroupLedgerStartupVerificationCli")
}

tasks.register<Test>("workE2eTest") {
	group = "verification"
	inputs.files(
		"../scripts/data-audit/domain-constraint-catalog.sql",
		"../scripts/data-audit/audit-domain-constraints.sql",
		"../scripts/data-audit/validate-domain-constraint.sql",
	)
	description = "Runs the Work API contract E2E tests against PostgreSQL."
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	useJUnitPlatform {
		includeTags("work-e2e")
	}
	shouldRunAfter(tasks.named("test"))
	systemProperty("greenhouse.cli.runtime-classpath", sourceSets["main"].runtimeClasspath.asPath)

	systemProperty("junit.jupiter.execution.timeout.threaddump.enabled", "true")
}

tasks.register<Test>("workBenchmark") {
	group = "verification"
	description = "Measures Work API query count and response-time distribution."
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	useJUnitPlatform {
		includeTags("work-benchmark")
	}
	maxParallelForks = 1
	systemProperty(
		"workBenchmark.enforceQueryLimits",
		providers.gradleProperty("workBenchmarkEnforce").orElse("false").get()
	)
	shouldRunAfter(tasks.named("workE2eTest"))
}

tasks.register<Test>("domainBenchmark") {
	group = "verification"
	description = "Measures settlement rebuild and ledger reconciliation in an isolated PostgreSQL database."
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	useJUnitPlatform { includeTags("domain-benchmark") }
	maxParallelForks = 1
	maxHeapSize = providers.gradleProperty("domainBenchmarkHeap").orElse("2g").get()
	for ((key, fallback) in mapOf(
		"profile" to "standard", "scenario" to "all", "samples" to "3", "warmup" to "1"
	)) {
		val value = providers.gradleProperty("domainBenchmark.$key").orElse(fallback)
		systemProperty("domainBenchmark.$key", value.get())
		inputs.property(key, value)
	}
	val reportDirectory = providers.gradleProperty("domainBenchmark.outputDir")
		.orElse(layout.buildDirectory.dir("domain-benchmark/direct").map { it.asFile.absolutePath })
	systemProperty("domainBenchmark.outputDir", reportDirectory.get())
	outputs.dir(reportDirectory)
	outputs.upToDateWhen { false }
	testLogging.showStandardStreams = true
	systemProperty("junit.jupiter.execution.timeout.threaddump.enabled", "true")
}

tasks.register<Test>("domainDiagnosis") {
	group = "verification"
	description = "Profiles selected settlement and ledger paths in isolated PostgreSQL."
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	useJUnitPlatform { includeTags("domain-diagnosis") }
	maxParallelForks = 1
	maxHeapSize = "2g"
	systemProperty("diagnosis.outputDir", providers.gradleProperty("diagnosis.outputDir")
		.orElse(layout.buildDirectory.dir("domain-diagnosis/direct").map { it.asFile.absolutePath }).get())
	systemProperty("diagnosis.revision", providers.gradleProperty("diagnosis.revision").orElse("unspecified").get())
	systemProperty("diagnosis.scope", providers.gradleProperty("diagnosis.scope").orElse("all").get())
	outputs.upToDateWhen { false }
	testLogging.showStandardStreams = true
	systemProperty("junit.jupiter.execution.timeout.threaddump.enabled", "true")
}
