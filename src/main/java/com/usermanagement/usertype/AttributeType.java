package com.usermanagement.usertype;

/** Value types supported for domain-specific user attributes. */
public enum AttributeType {
    STRING,
    INTEGER,
    NUMBER,
    BOOLEAN,
    /** ISO-8601 date, e.g. {@code 1990-05-31}. */
    DATE,
    /** One of {@code allowedValues}. */
    ENUM,
    EMAIL,
    /** E.164 phone number, e.g. {@code +251911234567}. */
    PHONE
}
