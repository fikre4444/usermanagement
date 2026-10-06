package com.usermanagement.otp;

/** What a one-time password proves. Codes for one purpose can never be used for another. */
public enum OtpPurpose {
    EMAIL_VERIFICATION("verify your email address"),
    PHONE_VERIFICATION("verify your phone number"),
    PASSWORD_RESET("reset your password"),
    LOGIN("sign in");

    private final String description;

    OtpPurpose(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
