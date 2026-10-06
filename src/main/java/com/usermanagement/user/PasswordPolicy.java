package com.usermanagement.user;

import com.usermanagement.common.error.ApiException;
import com.usermanagement.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Enforces the configurable password rules. */
@Component
public class PasswordPolicy {

    private final PasswordPolicyProperties rules;

    public PasswordPolicy(PasswordPolicyProperties rules) {
        this.rules = rules;
    }

    public void validate(String field, String password) {
        List<String> problems = new ArrayList<>();
        String value = password == null ? "" : password;
        if (value.length() < rules.minLength()) {
            problems.add("at least " + rules.minLength() + " characters");
        }
        if (value.length() > rules.maxLength()) {
            problems.add("at most " + rules.maxLength() + " characters");
        }
        if (rules.requireUppercase() && value.chars().noneMatch(Character::isUpperCase)) {
            problems.add("an uppercase letter");
        }
        if (rules.requireLowercase() && value.chars().noneMatch(Character::isLowerCase)) {
            problems.add("a lowercase letter");
        }
        if (rules.requireDigit() && value.chars().noneMatch(Character::isDigit)) {
            problems.add("a digit");
        }
        if (rules.requireSpecialCharacter() && value.chars().allMatch(Character::isLetterOrDigit)) {
            problems.add("a special character");
        }
        if (!problems.isEmpty()) {
            throw new ApiException(ErrorCode.PASSWORD_POLICY_VIOLATION,
                    ErrorCode.PASSWORD_POLICY_VIOLATION.defaultMessage(),
                    Map.of(field, "must contain " + String.join(", ", problems)));
        }
    }
}
