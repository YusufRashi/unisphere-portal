package com.unisphere.user;

import java.util.Arrays;

/** Application roles. The numeric ids match the rows seeded into the {@code roles} table by Flyway V1. */
public enum Role {
    STUDENT((short) 1),
    LECTURER((short) 2),
    ADMIN((short) 3),
    FINANCE((short) 4);

    private final short id;

    Role(short id) {
        this.id = id;
    }

    public short id() {
        return id;
    }

    /** Spring Security authority name, e.g. {@code ROLE_ADMIN}. */
    public String authority() {
        return "ROLE_" + name();
    }

    public static Role fromId(short id) {
        return Arrays.stream(values())
                .filter(r -> r.id == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown role id " + id));
    }
}
