package com.greenhouse.backend.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.greenhouse.backend")
class ModularMonolithArchitectureTest {

	@ArchTest
	static final ArchRule domain_model_must_not_depend_on_application_or_adapter_layers = noClasses()
		.that().resideInAPackage("..domain..")
		.should().dependOnClassesThat().resideInAnyPackage(
			"..application..",
			"..controller..",
			"..dto..",
			"..repository.."
		);

	@ArchTest
	static final ArchRule repositories_must_not_depend_on_web_or_application_layers = noClasses()
		.that().resideInAPackage("..repository..")
		.should().dependOnClassesThat().resideInAnyPackage(
			"..application..",
			"..controller..",
			"..dto.."
		);

	@ArchTest
	static final ArchRule controllers_must_not_access_repositories_directly = noClasses()
		.that().resideInAPackage("..controller..")
		.should().dependOnClassesThat().resideInAPackage("..repository..");

	@ArchTest
	static final ArchRule controllers_must_not_access_domain_entities_directly = noClasses()
		.that().resideInAPackage("..controller..")
		.should().dependOnClassesThat().resideInAPackage("..domain..");
}
