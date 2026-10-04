package com.unisphere.user;

import com.unisphere.common.security.CurrentUser;
import com.unisphere.common.web.PageResponse;
import com.unisphere.common.web.PageableFactory;
import com.unisphere.user.dto.AccountDtos.CreateStaffRequest;
import com.unisphere.user.dto.AccountDtos.UpdateUserRequest;
import com.unisphere.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin · Users")
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UserAdminController {

    private static final Map<String, String> SORTABLE = Map.of(
            "email", "email",
            "firstName", "firstName",
            "lastName", "lastName",
            "status", "status",
            "lastLoginAt", "lastLoginAt",
            "createdAt", "createdAt");

    private final UserAdminService service;
    private final AccountProvisioningService provisioning;

    @Operation(summary = "Search users")
    @GetMapping
    public PageResponse<UserResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        var pageable = PageableFactory.of(page, size, sort, SORTABLE, Sort.by("lastName", "firstName", "id"));
        return service.search(q, role, status, pageable);
    }

    @Operation(summary = "Get a user")
    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "Create an ADMIN and/or FINANCE staff account and email an activation link")
    @PostMapping("/staff")
    public ResponseEntity<UserResponse> createStaff(@Valid @RequestBody CreateStaffRequest body) {
        UserResponse created = provisioning.createStaff(body);
        return ResponseEntity.created(URI.create("/api/admin/users/" + created.id())).body(created);
    }

    @Operation(summary = "Update a user's name and phone number")
    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest body) {
        return provisioning.updateUser(id, body);
    }

    @Operation(summary = "Send a new activation link to a pending account")
    @PostMapping("/{id}/resend-activation")
    public ResponseEntity<Void> resendActivation(@PathVariable Long id) {
        provisioning.resendActivation(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Deactivate a user and revoke all sessions")
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id, CurrentUser.id());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reactivate a deactivated user")
    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        service.reactivate(id);
        return ResponseEntity.noContent().build();
    }
}
