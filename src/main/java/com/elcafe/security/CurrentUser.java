package com.elcafe.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.*;

/**
 * Annotation to inject the current authenticated user into controller methods
 *
 * Usage:
 * @GetMapping("/profile")
 * public ResponseEntity<?> getProfile(@CurrentUser UserPrincipal user) {
 *     // user is the currently authenticated user
 * }
 */
@Target({ElementType.PARAMETER, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal
public @interface CurrentUser {
}
