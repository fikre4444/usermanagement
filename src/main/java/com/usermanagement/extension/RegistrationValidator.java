package com.usermanagement.extension;

/**
 * Extension point for domain rules that cannot be expressed in the user type configuration, for
 * example "a driver must be at least 21 years old" or "the tax id must be unique".
 * <p>
 * Implement it as a Spring bean; it is called for every registration (self-service and admin)
 * after built-in validation. Throw a
 * {@link com.usermanagement.common.error.ApiException} to reject the registration, e.g.
 * {@code throw ApiException.validation("attributes.dateOfBirth", "driver must be at least 21")}.
 */
public interface RegistrationValidator {

    void validate(RegistrationContext context);
}
