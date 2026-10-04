package com.unisphere.user;

import jakarta.persistence.criteria.Join;
import com.unisphere.common.persistence.Specs;
import org.springframework.data.jpa.domain.Specification;

/** Composable filters for the admin user search. Values are bound as parameters (no SQL injection). */
final class UserSpecifications {

    private UserSpecifications() {
    }

    static Specification<User> matchesQuery(String q) {
        String pattern = Specs.containsPattern(q);
        if (pattern == null) {
            return null;
        }
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("email")), pattern, '\\'),
                cb.like(cb.lower(root.get("firstName")), pattern, '\\'),
                cb.like(cb.lower(root.get("lastName")), pattern, '\\'),
                cb.like(cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"))), pattern, '\\'));
    }

    static Specification<User> hasRole(Role role) {
        if (role == null) {
            return null;
        }
        return (root, query, cb) -> {
            query.distinct(true);
            Join<User, Role> roles = root.join("roles");
            return cb.equal(roles, role);
        };
    }

    static Specification<User> hasStatus(UserStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
