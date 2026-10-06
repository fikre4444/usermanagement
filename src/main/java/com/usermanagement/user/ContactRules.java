package com.usermanagement.user;

import com.usermanagement.usertype.UserType;
import java.util.Map;

/** Which contact details a user of a given type must have. */
final class ContactRules {

    private ContactRules() {
    }

    static void check(UserType type, String email, String phone, Map<String, String> errors) {
        if (type.emailRequired() && email == null) {
            errors.put("email", "is required");
        }
        if (type.phoneRequired() && phone == null) {
            errors.put("phone", "is required");
        }
        if (email == null && phone == null && !errors.containsKey("email") && !errors.containsKey("phone")) {
            errors.put("email", "an email address or a phone number is required");
        }
    }
}
