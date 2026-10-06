package com.usermanagement.common;

import java.util.Locale;

/** Normalisation of configuration keys such as user type codes and role names. */
public final class Codes {

    private Codes() {
    }

    /** {@code "fleet-manager"} becomes {@code "FLEET_MANAGER"}. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
