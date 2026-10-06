package com.usermanagement.extension;

import com.usermanagement.user.UserSnapshot;
import java.util.Map;

/**
 * Extension point to add domain-specific claims to access tokens, e.g. a {@code tenant_id} taken
 * from the user's attributes. Implement it as a Spring bean.
 * <p>
 * Reserved claims ({@code sub}, {@code iss}, {@code exp}, {@code iat}, {@code jti}, {@code roles},
 * {@code permissions}, {@code user_type}) cannot be overridden.
 */
public interface TokenClaimsCustomizer {

    void customize(UserSnapshot user, Map<String, Object> claims);
}
