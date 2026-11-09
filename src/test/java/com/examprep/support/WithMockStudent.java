package com.examprep.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.test.context.support.WithSecurityContext;

/** Authenticates the test as a real student row, so current-user lookups resolve. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@WithSecurityContext(factory = MockStudentSecurityContextFactory.class)
public @interface WithMockStudent {

    String email() default "student@example.edu";

    String name() default "Test Student";

    String password() default "password123";
}
