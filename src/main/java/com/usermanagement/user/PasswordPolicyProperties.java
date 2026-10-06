package com.usermanagement.user;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.password-policy")
public record PasswordPolicyProperties(
        @DefaultValue("8") int minLength,
        @DefaultValue("128") int maxLength,
        @DefaultValue("true") boolean requireUppercase,
        @DefaultValue("true") boolean requireLowercase,
        @DefaultValue("true") boolean requireDigit,
        @DefaultValue("false") boolean requireSpecialCharacter) {
}
