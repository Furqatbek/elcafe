package com.elcafe.modules.auth.controller;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.utils.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/v1/system-users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SystemUserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final Set<UserRole> SYSTEM_ROLES = Set.of(
            UserRole.ADMIN, UserRole.OWNER, UserRole.MANAGER, UserRole.OPERATOR);

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAll() {
        List<User> users = userRepository.findAll().stream()
                .filter(u -> SYSTEM_ROLES.contains(u.getRole()))
                .toList();

        List<Map<String, Object>> result = users.stream().map(u -> Map.<String, Object>of(
                "id", u.getId(),
                "email", u.getEmail() != null ? u.getEmail() : "",
                "firstName", u.getFirstName() != null ? u.getFirstName() : "",
                "lastName", u.getLastName() != null ? u.getLastName() : "",
                "phone", u.getPhone() != null ? u.getPhone() : "",
                "role", u.getRole().name(),
                "active", u.getActive()
        )).toList();

        return ResponseEntity.ok(ApiResponse.success("System users retrieved", result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@Valid @RequestBody CreateRequest req) {
        if (!SYSTEM_ROLES.contains(req.role)) {
            throw new IllegalArgumentException("Invalid role. Must be one of: " + SYSTEM_ROLES);
        }
        if (userRepository.existsByEmail(req.email)) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = User.builder()
                .email(req.email)
                .password(passwordEncoder.encode(req.password))
                .firstName(req.firstName)
                .lastName(req.lastName)
                .phone(req.phone)
                .role(req.role)
                .active(true)
                .emailVerified(true)
                .build();

        User saved = userRepository.save(user);
        log.info("System user created: {} ({})", saved.getEmail(), saved.getRole());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created", toMap(saved)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(@PathVariable Long id, @RequestBody UpdateRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (req.firstName != null) user.setFirstName(req.firstName);
        if (req.lastName != null) user.setLastName(req.lastName);
        if (req.phone != null) user.setPhone(req.phone);
        if (req.role != null && SYSTEM_ROLES.contains(req.role)) user.setRole(req.role);
        if (req.active != null) user.setActive(req.active);
        if (req.password != null && !req.password.isBlank()) {
            user.setPassword(passwordEncoder.encode(req.password));
        }

        User saved = userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success("User updated", toMap(saved)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(false);
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success("User deactivated", null));
    }

    private Map<String, Object> toMap(User u) {
        return Map.of(
                "id", u.getId(),
                "email", u.getEmail(),
                "firstName", u.getFirstName() != null ? u.getFirstName() : "",
                "lastName", u.getLastName() != null ? u.getLastName() : "",
                "phone", u.getPhone() != null ? u.getPhone() : "",
                "role", u.getRole().name(),
                "active", u.getActive()
        );
    }

    public record CreateRequest(
            @NotBlank @Email String email,
            @NotBlank String password,
            @NotBlank String firstName,
            @NotBlank String lastName,
            String phone,
            @NotNull UserRole role
    ) {}

    public record UpdateRequest(
            String firstName,
            String lastName,
            String phone,
            String password,
            UserRole role,
            Boolean active
    ) {}
}
