package com.greenhouse.backend.work.e2e;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/** Retains a fresh application/database for destructive migration and startup probes. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@TestPropertySource(properties = "test.postgres.isolated=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@interface IsolatedPostgresContext {}
