package com.usermanagement.usertype;

/** Who may write an attribute and when. Admins may always write every attribute. */
public enum AttributeAccess {
    /** The user provides it at registration and may change it later. */
    READ_WRITE,
    /** The user provides it at registration; afterwards only admins can change it. */
    WRITE_ONCE,
    /** Only admins set it (e.g. "backgroundCheckPassed"); the user can only read it. */
    ADMIN_ONLY
}
