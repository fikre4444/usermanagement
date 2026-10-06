package com.usermanagement.role;

/**
 * Permissions understood by this service. Domains are free to add their own permissions to roles
 * (e.g. {@code shipments:assign}); they are carried in the access token for other services to use.
 */
public final class Permissions {

    public static final String USERS_READ = "users:read";
    public static final String USERS_WRITE = "users:write";
    public static final String ROLES_READ = "roles:read";
    public static final String ROLES_WRITE = "roles:write";

    private Permissions() {
    }
}
