package com.org.ecommerce.validation;

import java.util.Locale;
import java.util.Set;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    static final int MIN = 10;
    static final int MAX = 128;

    /** Small built-in blocklist; extend with a breached-password list in production if required. */
    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password12", "password123", "password1234", "1234567890", "12345678910",
            "qwertyuiop", "qwerty12345", "qwerty123456", "iloveyou12", "letmein123", "welcome123", "admin12345",
            "administrator", "passw0rd123", "abc1234567", "1q2w3e4r5t", "1qaz2wsx3edc", "changeme123",
            "football123", "baseball123", "sunshine123", "princess123", "dragon12345", "monkey12345",
            "0123456789", "9876543210", "1111111111", "0000000000", "aaaaaaaaaa", "trustno1234");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // @NotBlank handles presence
        }
        int length = value.codePointCount(0, value.length());
        if (length < MIN || length > MAX) {
            return false;
        }
        if (value.chars().distinct().count() < 4) {
            return false;
        }
        return !COMMON.contains(value.toLowerCase(Locale.ROOT));
    }
}
