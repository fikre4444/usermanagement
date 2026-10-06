package com.usermanagement.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.usermanagement.common.error.ApiException;
import com.usermanagement.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy(
            new PasswordPolicyProperties(8, 128, true, true, true, true));

    @Test
    void acceptsStrongPasswords() {
        assertThatCode(() -> policy.validate("password", "Str0ng-Pass")).doesNotThrowAnyException();
    }

    @Test
    void listsEveryMissingRequirement() {
        assertThatThrownBy(() -> policy.validate("password", "weak"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATION);
                    assertThat(ex.errors().get("password"))
                            .contains("at least 8 characters", "an uppercase letter", "a digit", "a special character");
                });
    }
}
