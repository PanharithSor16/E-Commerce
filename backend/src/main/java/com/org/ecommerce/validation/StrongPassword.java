package com.org.ecommerce.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Password policy following NIST SP 800-63B: 10-128 characters, not a commonly used password.
 * No arbitrary composition rules.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "must be 10-128 characters and not a commonly used password";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
