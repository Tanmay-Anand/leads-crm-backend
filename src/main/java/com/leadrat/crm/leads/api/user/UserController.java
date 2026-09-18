package com.leadrat.crm.leads.api.user;

import com.leadrat.crm.leads.api.auth.annotations.AuthenticatedOnly;
import com.leadrat.crm.leads.api.core.UserRole;
import com.leadrat.crm.leads.api.user.dto.MeResponse;
import com.leadrat.crm.leads.api.user.dto.ResetPasswordRequest;
import com.leadrat.crm.leads.api.user.dto.UserDto;
import com.leadrat.crm.leads.api.user.dto.UserNamesDto;
import com.leadrat.crm.leads.api.user.dto.UserRequest;
import com.leadrat.crm.leads.api.user.dto.UserStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Who am I", description = "Provisions this user's local row on first call if it does "
            + "not exist yet (just-in-time provisioning) - a Cognito-only admin is already fully authorized "
            + "before this row exists, so this never gates an authorization decision, only a display.")
    @GetMapping("/me")
    @AuthenticatedOnly
    public ResponseEntity<MeResponse> getMe() {
        return ResponseEntity.ok(userService.getMe());
    }

    @Operation(summary = "Get all users as id/display-name pairs",
            description = "Feeds the lead assignee dropdown. Open to any authenticated user, not gated by "
                    + "view:users - every user needs it to assign a lead to a colleague.")
    @GetMapping("/names")
    @AuthenticatedOnly
    public ResponseEntity<List<UserNamesDto>> getNames() {
        return ResponseEntity.ok(userService.getNames());
    }

    @Operation(summary = "Get users with pagination")
    @GetMapping
    @PreAuthorize("@permissionService.check('view', 'users')")
    public ResponseEntity<Page<UserDto>> getAll(
            @Parameter(description = "Free-text search term, minimum 2 characters.")
            @RequestParam(required = false) String q,
            @Parameter(description = "Comma-separated field names to search. Omit to search all fields.")
            @RequestParam(required = false) List<UserSearchField> searchFields,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) UUID customRoleId,
            @ParameterObject @PageableDefault(sort = "created", direction = Sort.Direction.DESC) Pageable pageable) {

        UserListFilters filters = new UserListFilters(enabled, role, customRoleId);
        if (q != null && q.trim().length() >= 2) {
            return ResponseEntity.ok(userService.simpleSearch(q.trim(), searchFields, pageable, filters));
        }
        return ResponseEntity.ok(userService.getAll(pageable, filters));
    }

    @Operation(summary = "Get a user by ID")
    @GetMapping("/{id}")
    @PreAuthorize("@permissionService.check('view', 'users')")
    public ResponseEntity<UserDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.get(id));
    }

    @Operation(summary = "Create a user", description = "Provisions the user in Cognito and locally.")
    @PostMapping
    @PreAuthorize("@permissionService.check('add', 'users')")
    public ResponseEntity<UserDto> create(@Valid @RequestBody UserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @Operation(summary = "Update a user", description = "No password field - see UserDto's class doc.")
    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.check('update', 'users')")
    public ResponseEntity<UserDto> edit(@PathVariable UUID id, @Valid @RequestBody UserDto request) {
        return ResponseEntity.ok(userService.edit(id, request));
    }

    @Operation(summary = "Enable or disable a user", description = "Refused for self, and for any admin.")
    @PatchMapping("/{id}/status")
    @PreAuthorize("@permissionService.check('toggle', 'users')")
    public ResponseEntity<UserDto> setStatus(@PathVariable UUID id, @Valid @RequestBody UserStatusRequest request) {
        return ResponseEntity.ok(userService.setStatus(id, request.enabled()));
    }

    @Operation(summary = "Reset a user's password",
            description = "Out-of-band reset via Cognito - kept off UserDto's edit shape entirely so a stray "
                    + "edit-form submission can never rotate someone else's password.")
    @PatchMapping("/{id}/password")
    @PreAuthorize("@permissionService.check('update', 'users')")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request.password());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Soft-delete a user",
            description = "Disables the Cognito identity rather than deleting it - a hard delete would free the "
                    + "email for a new sub while leads still point at the old one.")
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.check('delete', 'users')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
