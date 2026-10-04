package com.unisphere.user;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores {@link Role} as its SMALLINT id so {@code user_roles.role_id} can reference {@code roles.id}. */
@Converter
public class RoleIdConverter implements AttributeConverter<Role, Short> {

    @Override
    public Short convertToDatabaseColumn(Role role) {
        return role == null ? null : role.id();
    }

    @Override
    public Role convertToEntityAttribute(Short id) {
        return id == null ? null : Role.fromId(id);
    }
}
